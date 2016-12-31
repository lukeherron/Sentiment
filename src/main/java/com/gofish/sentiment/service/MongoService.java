package com.gofish.sentiment.service;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
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
public interface MongoService {

    static MongoService create(Vertx vertx, JsonObject config) {
        return new MongoServiceImpl(vertx, config);
    }

    static MongoService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(MongoService.class, vertx, address);
    }

    @Fluent
    MongoService createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    MongoService createIndex(String collectionName, String indexName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    MongoService getCollections(Handler<AsyncResult<JsonArray>> resultHandler);

    @Fluent
    MongoService hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    MongoService saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler);

    @ProxyClose
    void close();
}
