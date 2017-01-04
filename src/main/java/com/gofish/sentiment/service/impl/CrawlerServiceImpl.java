package com.gofish.sentiment.service.impl;

import com.gofish.sentiment.service.CrawlerService;
import com.gofish.sentiment.service.MongoService;
import com.gofish.sentiment.verticle.MongoVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author Luke Herron
 */
public class CrawlerServiceImpl implements CrawlerService {

    private static final String API_KEY = "5b4d7fbf32fd42a58689ff4aee0eaee0";
    private static final String API_BASE_URL = "api.cognitive.microsoft.com";
    private static final String API_URL_PATH = "/bing/v5.0/news/search";
    private static final int API_PORT = 443;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    private final Vertx vertx;
    private final JsonObject config;

    public CrawlerServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    @Override
    public CrawlerService startCrawl(Handler<AsyncResult<JsonArray>> resultHandler) {
        MongoService mongoService = MongoService.createProxy(vertx, MongoVerticle.ADDRESS);
        mongoService.getCollections(handler -> {
            if (handler.succeeded()) {
                JsonArray queries = handler.result();
                crawlQueries(queries).setHandler(resultHandler);
            }
            else {
                logger.error(handler.cause().getMessage(), handler.cause());
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });

        return this;
    }

    private Future<JsonArray> crawlQueries(JsonArray queries) {
        // We will re-use a single client and enable pipe-lining to keep sending requests over it, this should improve
        // performance as we are required to enable SSL over this connection. As this scales we will likely want to
        // enable more clients to reduce any bottlenecks. When pipe-lining is enabled the connection is automatically
        // closed when all in-flight responses have returned and there are no outstanding pending requests to write, so
        // we don't need to worry about closing the client connection ourselves.
        Future<JsonArray> allCrawlsCompleteFuture = Future.future();
        List<Future<JsonObject>> crawlQueryFutures = new CopyOnWriteArrayList<>();
        HttpClient client = vertx.createHttpClient(getHttpClientOptions());
        JsonArray crawlResults = new JsonArray();

        queries.forEach(q -> {
            String query = q.toString();
            // We will want to be able to notify any callers when the crawl has finished, so we will need to gather
            // individual futures and indicate once they are all complete
            Future<JsonObject> crawlQueryFuture = crawlQuery(client, query);
            crawlQueryFutures.add(crawlQueryFuture);

            crawlQueryFuture.setHandler(handler -> {
                if (handler.succeeded()) {
                    JsonObject crawlerResponse = handler.result();
                    crawlResults.add(crawlerResponse);
                    System.out.println("Crawl complete: " + query);
                    // TODO: send for sentiment analysis

                    boolean isComplete = crawlQueryFutures.stream().allMatch(Future::isComplete);
                    if (isComplete) {
                        allCrawlsCompleteFuture.complete(crawlResults);
                    }
                }
                else {
                    logger.error(handler.cause().getMessage(), handler.cause());
                }
            });
        });

        return allCrawlsCompleteFuture;
    }

    private Future<JsonObject> crawlQuery(HttpClient client, String query) {
        String request = String.join("", API_URL_PATH, "?q=", query);

        Future<JsonObject> queryResponseFuture = Future.future();
        client.get(API_PORT, API_BASE_URL, request, responseHandler -> responseHandler.bodyHandler(buffer -> {
            int status = responseHandler.statusCode();
            if (status >= 200 && status < 300 || status == 304) {
                JsonObject response = parseResponse(buffer.toJsonObject());
                addMissingContext(response, query);
                queryResponseFuture.complete(response);
            }
            else {
                queryResponseFuture.fail(responseHandler.statusMessage());
            }
        })).putHeader("Ocp-Apim-Subscription-Key", API_KEY).end();

        return queryResponseFuture;
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
    public void close() {
        logger.info("Closing Crawler Service");
    }
}
