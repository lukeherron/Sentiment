package com.gofish.sentiment.verticle;

import com.gofish.sentiment.rxjava.service.CrawlerService;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.RxHelper;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientResponse;
import io.vertx.serviceproxy.ProxyHelper;
import rx.Observable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Luke Herron
 */
public class CrawlerVerticle extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.crawler";
    private static final String API_BASE_URL = "api.cognitive.microsoft.com";
    private static final String API_URL_PATH = "/bing/v5.0/news/search";
    private static final int API_PORT = 443;
    public static final int TIMER_DELAY = 3600000;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerVerticle.class);

    private String apiKey;
    private CrawlerService crawlerService;
    private MessageConsumer<JsonObject> consumer;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        // We have no idea how long the crawl will take to complete, but we want it to run every TIMER_DELAY interval.
        // We will need to time how long each crawl takes. If it is less than TIMER_DELAY, then we simply set a timer
        // for the time remaining. If it is greater than or equal to TIMER_DELAY, then we will want to re-launch
        // immediately (and probably warn that we need more resources for timely completion)
        // N.B. Vertx timer values must be greater than 0
        // TODO: implement functionality as discussed in above comment, probably simpler to use backpressure
        apiKey = config().getString("api.key");
        crawlerService = CrawlerService.create(vertx, config());
        consumer = ProxyHelper.registerService(com.gofish.sentiment.service.CrawlerService.class, getVertx(), (com.gofish.sentiment.service.CrawlerService) crawlerService.getDelegate(), ADDRESS);

        startCrawl().subscribe(
                result -> logger.debug("Crawl completed"),
                startFuture::fail,
                startFuture::complete
        );
    }

    private Observable<Void> startCrawl() {
        return crawlerService.getQueriesObservable()
                .flatMap(this::crawlQueries)
                .doOnNext(logger::info)
                .map(response -> null);
    }

    private Observable<JsonArray> crawlQueries(JsonArray queries) {
        HttpClient httpClient = vertx.createHttpClient(getHttpClientOptions());

        return Observable.from(queries)
                .flatMap(query -> crawlQuery(query.toString(), httpClient))
                .toList()
                .map(JsonArray::new);
    }

    private Observable<JsonObject> crawlQuery(String query, HttpClient httpClient) {
        String requestUri = String.join("", API_URL_PATH, "?q=", query);
        MultiMap headers = new MultiMap(new CaseInsensitiveHeaders().add("Ocp-Apim-Subscription-Key", apiKey));

        return RxHelper.get(httpClient, API_PORT, API_BASE_URL, requestUri, headers)
                .doOnNext(response -> {
                    int status = response.statusCode();
                    if (!(status >= 200 && status < 300 || status == 304)) logger.warn(response.statusMessage());
                })
                .filter(response -> response.statusCode() >= 200 && response.statusCode() < 300 || response.statusCode() == 304)
                .flatMap(this::mapFullBufferToObservable)
                .map(this::parseResponse);
    }

    private Observable<JsonObject> mapFullBufferToObservable(HttpClientResponse response) {
        ObservableFuture<JsonObject> observable = io.vertx.rx.java.RxHelper.observableFuture();
        response.bodyHandler(buffer -> observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject())));
        return observable;
    }

    private JsonObject parseResponse(JsonObject response) {
        JsonObject copy = response.copy(); // We will be changing the structure of the json, so make a copy to change.
        JsonArray articles = copy.getJsonArray("value").copy();

        // Our json object can contain nested json representing 'associated' articles (referred to as clusteredArticles
        // by the API). We want these articles to be stored as their own entry in the DB, so we extract them into a
        // separate array and remove the nested values from the original json object (i.e. the copy of the original)
        JsonArray associatedArticles = extractAssociatedArticles(articles);

        if (associatedArticles.size() > 0) {
            articles.addAll(associatedArticles);
        }

        copy.getJsonArray("value").clear().addAll(articles);

        return copy;
    }

    private JsonArray extractAssociatedArticles(JsonArray articles) {
        JsonArray associatedArticles = new JsonArray();

        // For each article in the json response, we check to see if there are any clustered articles. We want to rank
        // these articles as well, but we don't want them nested inside other articles. We essentially are going to
        // 'flatten' the clustered articles by removing them as nested objects and placing them as root articles. We
        // start with the intermediary step of extracting them to a seperate JsonArray
        articles.stream().map(article -> (JsonObject) article).forEach(article -> {
            if (article.containsKey("clusteredArticles")) {
                JsonArray clusteredArticles = article.getJsonArray("clusteredArticles");
                clusteredArticles.forEach(clusteredArticle -> {
                    JsonArray clusteredArticleAbout = ((JsonObject) clusteredArticle).getJsonArray("about");

                    // Iterate the "about" entries of the parent article and check to see if they exist in the clustered
                    // article (via filter), if an entry doesn't exist in clustered article, copy it across from parent
                    article.getJsonArray("about").stream()
                            .filter(aboutEntry -> !clusteredArticleAbout.contains(aboutEntry))
                            .forEach(clusteredArticleAbout::add);

                    associatedArticles.add(clusteredArticle);
                });

                // The whole point is to flatten out the articles, so we can go ahead and remove the "clusteredArticles"
                // entry from the json now that we've copied them up into the parent structure
                article.remove("clusteredArticles");
            }
        });

        return associatedArticles;
    }

    private void addMissingContext(JsonObject response, String query) {
        List<JsonObject> noContextArticles = getNoContextArticles(response.getJsonArray("value"), query);
        noContextArticles.forEach(article -> {
            // TODO: pass through context linking API once verticle has been coded
        });
    }

    private List<JsonObject> getNoContextArticles(JsonArray articles, String query) {
        return articles.stream()
                .map(article -> (JsonObject) article)
                .filter(article -> isWithoutQueryContext(query, article))
                .collect(Collectors.toList());
    }

    private boolean isWithoutQueryContext(String query, JsonObject article) {
        return article.getJsonArray("about").stream()
                .map(about -> (JsonObject) about)
                .noneMatch(about -> about.getString("name").toLowerCase().contains(query));
    }

    /**
     * Creates and returns an HttpClientOptions object. Values for each option are retrieved from the config json object
     * (this config json object is passed in to the verticle when it is deployed)
     *
     * @return HttpClientOptions object to configure this verticles HttpClient
     */
    private HttpClientOptions getHttpClientOptions() {
        return new HttpClientOptions()
                .setPipelining(true)
                .setPipeliningLimit(8)
                .setIdleTimeout(2)
                .setSsl(true)
                .setKeepAlive(true);
    }

    @Override
    public void stop() throws Exception {
        consumer.unregister();
        //crawlerService.close();
    }
}
