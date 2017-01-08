package com.gofish.sentiment.service;

import com.gofish.sentiment.service.impl.CrawlerServiceImpl;
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
public interface CrawlerService {

    static CrawlerService create(Vertx vertx, JsonObject config) {
        return new CrawlerServiceImpl(vertx, config);
    }

    static CrawlerService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(CrawlerService.class, vertx, address);
    }

    void addNewQuery(String query, Handler<AsyncResult<Void>> resultHandler);

    void getQueries(Handler<AsyncResult<JsonArray>> resultHandler);

    void isQueryActive(String query, Handler<AsyncResult<Boolean>> resultHandler);

    void saveArticles(String query, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler);
}
