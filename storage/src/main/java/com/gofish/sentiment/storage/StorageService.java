package com.gofish.sentiment.storage;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
@ProxyGen
@VertxGen
public interface StorageService {

    String NAME = "sentiment-eventbus-service";
    String ADDRESS = "sentiment.storage";

    static StorageService create(Vertx vertx, JsonObject config) {
        return new StorageServiceImpl(vertx, config);
    }

    static StorageService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(StorageService.class, vertx, address);
    }

    void createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler);

    void createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler);

    void getCollections(Handler<AsyncResult<JsonArray>> resultHandler);

    void getSentimentResults(String collectionName, Handler<AsyncResult<JsonObject>> resultHandler);

    void hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler);

    void isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler);

    void saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler);
}
