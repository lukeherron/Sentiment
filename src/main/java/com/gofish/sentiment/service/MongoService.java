package com.gofish.sentiment.service;

import com.gofish.sentiment.service.impl.MongoServiceImpl;
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
public interface MongoService {

    static MongoService create(Vertx vertx, JsonObject config) {
        return new MongoServiceImpl(vertx, config);
    }

    static MongoService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(MongoService.class, vertx, address);
    }

    void createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler);

    void createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler);

    void getCollections(Handler<AsyncResult<JsonArray>> resultHandler);

    void hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler);

    void saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler);

    void isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler);
}
