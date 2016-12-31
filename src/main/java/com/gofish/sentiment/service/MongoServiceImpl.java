package com.gofish.sentiment.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;
import java.util.Optional;

/**
 * @author Luke Herron
 */
public class MongoServiceImpl implements MongoService {

    private static final Logger logger = LoggerFactory.getLogger(MongoServiceImpl.class);
    private MongoClient client;

    public MongoServiceImpl(Vertx vertx, JsonObject config) {
        client = MongoClient.createShared(vertx, config);
    }

    @Override
    public MongoService createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) {
        hasCollection(collectionName, handler -> {
            Boolean isPresent = Optional.ofNullable(handler.result()).orElse(false);
            if (!isPresent) {
                client.createCollection(collectionName, resultHandler);
            }
            else {
                resultHandler.handle(null); // Collection already exists, nothing to do...
            }
        });

        return this;
    }

    @Override
    public MongoService createIndex(String indexName, String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler) {
        isIndexPresent(indexName, collectionName).setHandler(handler -> {
            if (handler.succeeded() && !handler.result()) {
                IndexOptions indexOptions = new IndexOptions().name(collectionName + "Index").unique(true);
                client.createIndexWithOptions(collectionName, collectionIndex, indexOptions, resultHandler);
            }
            else if (handler.failed()) {
                logger.error(handler.cause().getMessage(), handler.cause());
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });

        return this;
    }

    @Override
    public MongoService getCollections(Handler<AsyncResult<JsonArray>> resultHandler) {
        client.getCollections(handler -> {
            if (handler.succeeded()) {
                JsonArray collections = new JsonArray(handler.result());
                resultHandler.handle(Future.succeededFuture(collections));
            }
        });

        return this;
    }

    @Override
    public MongoService hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {
        client.getCollections(handler -> {
            if (handler.succeeded()) {
                List<String> collections = handler.result();
                boolean collectionExists = collections.contains(collectionName);
                resultHandler.handle(Future.succeededFuture(collectionExists));
            }
            else {
                logger.error(handler.cause().getMessage(), handler.cause());
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });

        return this;
    }

    @Override
    public MongoService saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) {
        // TODO: do we need to consider the fact that news articles can be updated?
        JsonObject command = new JsonObject()
                .put("insert", collectionName)
                .put("document", articles)
                .put("ordered", false);

        client.runCommand("insert", command, resultHandler);

        return this;
    }

    private Future<Boolean> isIndexPresent(String indexName, String collectionName) {
        Future<Boolean> isIndexPresentFuture = Future.future();

        client.listIndexes(collectionName, resultHandler -> {
            if (resultHandler.succeeded()) {
                JsonArray indexes = resultHandler.result();
                boolean indexExists = indexes.contains(indexName);
                isIndexPresentFuture.complete(indexExists);}
            else {
                logger.error(resultHandler.cause().getMessage(), resultHandler.cause());
                isIndexPresentFuture.fail(resultHandler.cause());}
        });

        return isIndexPresentFuture;
    }

    @Override
    public void close() {
        client.close();
    }
}
