package com.gofish.sentiment.verticle;

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
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientResponse;
import rx.Single;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Luke Herron
 */
public class CrawlerWorker extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.crawler.worker";
    private static final Logger logger = LoggerFactory.getLogger(CrawlerWorker.class);

    private String apiKey;
    private HttpClient httpClient;
    private MessageConsumer<Object> messageConsumer;

    @Override
    public void start() throws Exception {
        apiKey = config().getString("api.key", "");
        httpClient = vertx.createHttpClient(getHttpClientOptions());
        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, this::messageHandler);
    }

    private void messageHandler(Message<Object> message) {
        String action = message.headers().get("action");
        JsonObject messageBody = (JsonObject) message.body();

        switch (action) {
            case "crawlQuery":
                crawlQuery(messageBody, message);
                break;
            default:
                message.reply("Invalid Action");
        }
    }

    private void crawlQuery(JsonObject messageBody, Message<Object> message) {
        final String query = messageBody.getString("query");
        final String requestUri = String.join("", CrawlerVerticle.API_URL_PATH, "?q=", query);
        final MultiMap headers = new MultiMap(new CaseInsensitiveHeaders().add("Ocp-Apim-Subscription-Key", apiKey));

        logger.info("Crawling query: " + query);
        RxHelper.get(httpClient, CrawlerVerticle.API_PORT, CrawlerVerticle.API_BASE_URL, requestUri, headers)
                .doOnNext(response -> logger.info(response.statusCode() + ": " + response.statusMessage()))
                .toSingle()
                .flatMap(this::mapFullBufferToObservable)
                .map(this::parseResponse)
                .subscribe(
                        result -> message.reply(result),
                        failure -> message.fail(1, failure.getCause().getMessage())
                );
    }

    private Single<JsonObject> crawlQuery(String query, HttpClient httpClient) {
        String requestUri = String.join("", CrawlerVerticle.API_URL_PATH, "?q=", query);
        MultiMap headers = new MultiMap(new CaseInsensitiveHeaders().add("Ocp-Apim-Subscription-Key", apiKey));

        return RxHelper.get(httpClient, CrawlerVerticle.API_PORT, CrawlerVerticle.API_BASE_URL, requestUri, headers)
                .toSingle()
                .flatMap(this::mapFullBufferToObservable)
                .map(this::parseResponse);
    }

    private Single<JsonObject> mapFullBufferToObservable(HttpClientResponse response) {
        ObservableFuture<JsonObject> observable = io.vertx.rx.java.RxHelper.observableFuture();
        response.bodyHandler(buffer -> {
            observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject()));
        });

        return observable.toSingle();
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
                    try {
                        preserveEntityContext(associatedArticles, article);
                    }
                    catch (Throwable t) {
                        t.printStackTrace();
                    }
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
        // It is possible that neither of our articles contains an about section, so provide an empty json array if they
        // don't
        JsonArray clusteredArticleAbout = Optional.ofNullable(clusteredArticle.getJsonArray("about")).orElse(new JsonArray());
        JsonArray parentArticleAbout = Optional.ofNullable(article.getJsonArray("about")).orElse(new JsonArray());

        // Iterate the "about" entries of the parent article and check to see if they exist in the
        // clustered article, if an entry doesn't exist in clustered article, copy it across from parent
        parentArticleAbout.stream()
                .filter(aboutEntry -> !clusteredArticleAbout.contains(aboutEntry))
                .forEach(clusteredArticleAbout::add);
    }

    private void addMissingContext(JsonObject response, String query) {
        // TODO: Save current work to persistence
        // This step requires hitting another API and waiting for results. We don't want to lose the work we've
        // done in the event of API issues etc., so make sure we have our current work saved, or implement something
        // that saves when an error is hit
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
    public void stop(Future<Void> stopFuture) throws Exception {
        httpClient.close();
        messageConsumer.unregisterObservable().subscribe(
                stopFuture::complete,
                stopFuture::fail,
                () -> logger.info("Crawler Worker consumer unregistered")
        );
    }
}
