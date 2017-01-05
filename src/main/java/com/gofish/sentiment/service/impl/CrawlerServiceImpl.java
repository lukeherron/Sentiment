package com.gofish.sentiment.service.impl;

import com.gofish.sentiment.service.CrawlerService;
import com.gofish.sentiment.service.MongoWrapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class CrawlerServiceImpl extends MongoWrapper implements CrawlerService {

    private static final String API_KEY = "5b4d7fbf32fd42a58689ff4aee0eaee0";
    private static final String API_BASE_URL = "api.cognitive.microsoft.com";
    private static final String API_URL_PATH = "/bing/v5.0/news/search";
    private static final int API_PORT = 443;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    private final Vertx vertx;

    public CrawlerServiceImpl(Vertx vertx, JsonObject config) {
        super(io.vertx.rxjava.core.Vertx.newInstance(vertx), config);
        this.vertx = vertx;
    }

    @Override
    public CrawlerService getQueries(Handler<AsyncResult<JsonArray>> resultHandler) {
        getCollections().subscribe(
                queries -> resultHandler.handle(Future.succeededFuture(queries)),
                failure -> resultHandler.handle(Future.failedFuture(failure)),
                () -> logger.debug("Completed getQueries() call")
        );

        return this;
    }

    /*
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
    */

    @Override
    public void close() {
        logger.info("Closing Crawler Service");
    }
}
