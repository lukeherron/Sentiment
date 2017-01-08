package com.gofish.sentiment.service.impl;

import com.gofish.sentiment.service.CrawlerService;
import com.gofish.sentiment.service.MongoService;
import com.gofish.sentiment.verticle.MongoVerticle;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class CrawlerServiceImpl implements CrawlerService {

    private static final int WORKER_INSTANCES = 2;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    private final Vertx vertx;
    private final DeploymentOptions workerOptions;
    private final MongoService mongoService;

    private final JsonObject collectionIndex = new JsonObject()
            .put("name", 1)
            .put("datePublished", 1)
            .put("description", 1);

    public CrawlerServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.workerOptions = new DeploymentOptions().setConfig(config).setWorker(true).setInstances(WORKER_INSTANCES);
        this.mongoService = MongoService.createProxy(vertx, MongoVerticle.ADDRESS);
    }

    @Override
    public void addNewQuery(String query, Handler<AsyncResult<Void>> resultHandler) {
        // To add a new query, we need to create a collection in mongo to store results. We also need to create an
        // index for the new collection to avoid duplicates.
        Future<Void> addNewQueryFuture = Future.future();
        Future<Void> createCollectionFuture = Future.future();

        mongoService.createCollection(query, createCollectionFuture.completer());

        // We'll use Vertx's compose() to ensure that the collection is created before the index
        createCollectionFuture.compose(v -> {
            Future<Void> createIndexFuture = Future.future();
            mongoService.createIndex(query, collectionIndex, createIndexFuture.completer());
        }, addNewQueryFuture);

        resultHandler.handle(addNewQueryFuture);
    }

    @Override
    public void isQueryActive(String query, Handler<AsyncResult<Boolean>> resultHandler) {
        mongoService.hasCollection(query, resultHandler);
    }

    @Override
    public void saveArticles(String query, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) {
        mongoService.saveArticles(query, articles, resultHandler);
    }

    @Override
    public void getQueries(Handler<AsyncResult<JsonArray>> resultHandler) {
        mongoService.getCollections(resultHandler);
    }
}

