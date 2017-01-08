package com.gofish.sentiment.service.impl;

import com.gofish.sentiment.service.CrawlerService;
import com.gofish.sentiment.service.MongoService;
import com.gofish.sentiment.verticle.CrawlerWorker;
import com.gofish.sentiment.verticle.MongoVerticle;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class CrawlerServiceImpl implements CrawlerService {

    private static final int WORKER_INSTANCES = 2;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    private final Vertx vertx;
    private final DeploymentOptions workerOptions;
    private final MongoService mongoService;

    public CrawlerServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.workerOptions = new DeploymentOptions().setConfig(config).setWorker(true).setInstances(WORKER_INSTANCES);
        this.mongoService = MongoService.createProxy(vertx, MongoVerticle.ADDRESS);
    }

    @Override
    public void addNewQuery(String query, Handler<AsyncResult<Void>> resultHandler) {
        mongoService.createCollection(query, resultHandler);
    }

    @Override
    public void isQueryActive(String query, Handler<AsyncResult<Boolean>> resultHandler) {
        mongoService.hasCollection(query, resultHandler);
    }

    @Override
    public void startCrawl(Handler<AsyncResult<JsonArray>> resultHandler) {
        mongoService.getCollections(handler -> {
            if (handler.succeeded()) {
                JsonArray queries = handler.result();
                if (queries.size() < WORKER_INSTANCES) workerOptions.setInstances(queries.size());
                if (!queries.isEmpty()) crawlQueries(queries, resultHandler);
            }
            else {
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });
    }

    private void crawlQueries(JsonArray queries, Handler<AsyncResult<JsonArray>> resultHandler) {
        vertx.deployVerticle("com.gofish.sentiment.verticle.CrawlerWorker", workerOptions, completionHandler -> {
            if (completionHandler.succeeded()) {
                DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "crawlQuery");
                JsonArray queryResults = new JsonArray();

                // Send each query to a worker verticle for processing
                queries.forEach(query -> {
                    JsonObject message = new JsonObject().put("query", query);
                    vertx.eventBus().send(CrawlerWorker.ADDRESS, message, deliveryOptions, replyHandler -> {
                        if (replyHandler.succeeded()) {
                            JsonObject result = Optional.ofNullable(replyHandler.result().body())
                                    .map(body -> (JsonObject) body)
                                    .orElse(new JsonObject().put("failed", "No response returned for query"));

                            queryResults.add(result);

                            // Monitor the number of queries and the number of query results, as this determines when we
                            // can mark the resultHandler as succeeded
                            if (queryResults.size() == queries.size()) {
                                resultHandler.handle(Future.succeededFuture(queryResults));
                            }
                        }
                        else {
                            logger.error(replyHandler.cause().getMessage(), replyHandler.cause());
                        }
                    });
                });
            }
            else {
                resultHandler.handle(Future.failedFuture(completionHandler.cause()));
            }

            vertx.undeploy(completionHandler.result());
        });
    }
}

