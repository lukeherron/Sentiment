package com.gofish.sentiment.storage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
public class StorageServiceImpl implements StorageService {

    public StorageServiceImpl(Vertx vertx, JsonObject config) {

    }

    @Override
    public void createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) {

    }

    @Override
    public void createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler) {

    }

    @Override
    public void getCollections(Handler<AsyncResult<JsonArray>> resultHandler) {

    }

    @Override
    public void getSentimentResults(String collectionName, Handler<AsyncResult<JsonObject>> resultHandler) {

    }

    @Override
    public void hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {

    }

    @Override
    public void isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {

    }

    @Override
    public void saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) {

    }
}
