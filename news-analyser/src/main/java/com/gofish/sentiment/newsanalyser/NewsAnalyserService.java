package com.gofish.sentiment.newsanalyser;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
@ProxyGen
@VertxGen
public interface NewsAnalyserService {

    String NAME = "newsanalyser-eventbus-service";
    String ADDRESS = "sentiment.service.analyser";

    static NewsAnalyserService create(Vertx vertx, JsonObject config) {
        return new NewsAnalyserServiceImpl(vertx, config);
    }

    static NewsAnalyserService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(NewsAnalyserService.class, vertx, address);
    }

    void analyseSentiment(JsonObject json, Handler<AsyncResult<JsonObject>> resultHandler);
}
