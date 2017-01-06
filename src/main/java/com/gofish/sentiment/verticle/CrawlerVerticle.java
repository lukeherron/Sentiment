package com.gofish.sentiment.verticle;

import com.gofish.sentiment.rxjava.service.MongoService;
import io.vertx.core.Future;
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
    private MongoService mongoService;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        // We have no idea how long the crawl will take to complete, but we want it to run every TIMER_DELAY interval.
        // We will need to time how long each crawl takes. If it is less than TIMER_DELAY, then we simply set a timer
        // for the time remaining. If it is greater than or equal to TIMER_DELAY, then we will want to re-launch
        // immediately (and probably warn that we need more resources for timely completion)
        // N.B. Vertx timer values must be greater than 0
        // TODO: implement functionality as discussed in above comment, probably simpler to use backpressure
        apiKey = config().getString("api.key", "");
        apiKey = "";

        RxHelper.deployVerticle(vertx, new MongoVerticle())
                .doOnNext(result -> mongoService = MongoService.createProxy(vertx, MongoVerticle.ADDRESS))
                .flatMap(result -> startCrawl())
                .doOnNext(logger::info)
                .subscribe(
                        result -> startFuture.complete(),
                        startFuture::fail,
                        () -> logger.info("Crawl completed")
                );
    }

    private Observable<JsonArray> startCrawl() {
        return mongoService.getCollectionsObservable()
                .flatMap(this::crawlQueries);
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
                .flatMap(this::mapFullBufferToObservable)
                .map(this::parseResponse);
    }

    private Observable<JsonObject> mapFullBufferToObservable(HttpClientResponse response) {
        ObservableFuture<JsonObject> observable = io.vertx.rx.java.RxHelper.observableFuture();
        response.bodyHandler(buffer -> observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject())));

        return observable;
    }

    private JsonObject parseResponse(JsonObject response) {
        if (!response.containsKey("value")) {
            // We didn't receive the expected results, so don't bother parsing and simply return the response for the
            // client to inspect.
            return response;
        }

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
        articles.stream().map(article -> (JsonObject) article)
                .filter(article -> article.containsKey("clusteredArticles"))
                .forEach(article -> {
                    // Generally clustered articles don't duplicate the entity context (i.e. "about" section values) of
                    // the parent article. We don't want to lose this info when we move the clustered article, so we
                    // ensure that we copy across any missing info first.
                    preserveEntityContext(associatedArticles, article);
                    article.remove("clusteredArticles");
                });

        return associatedArticles;
    }

    private void preserveEntityContext(JsonArray associatedArticles, JsonObject article) {
        JsonArray clusteredArticles = article.getJsonArray("clusteredArticles");
        clusteredArticles.forEach(clusteredArticle -> {
            copyMissingEntities(article, (JsonObject) clusteredArticle);
            associatedArticles.add(clusteredArticle);
        });
    }

    private void copyMissingEntities(JsonObject article, JsonObject clusteredArticle) {
        JsonArray clusteredArticleAbout = clusteredArticle.getJsonArray("about");
        JsonArray parentArticleAbout = article.getJsonArray("about");

        // Iterate the "about" entries of the parent article and check to see if they exist in the
        // clustered article, if an entry doesn't exist in clustered article, copy it across from parent
        parentArticleAbout.stream()
                .filter(aboutEntry -> !clusteredArticleAbout.contains(aboutEntry))
                .forEach(clusteredArticleAbout::add);
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
        //mongoService.close();
    }
}
