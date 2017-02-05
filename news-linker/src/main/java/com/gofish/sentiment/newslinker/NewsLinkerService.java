package com.gofish.sentiment.newslinker;

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
public interface NewsLinkerService {

    String NAME = "newslinker-eventbus-service";
    String ADDRESS = "sentiment.service.linker";

    static NewsLinkerService create(Vertx vertx, JsonObject config) {
        return new NewsLinkerServiceImpl(vertx, config);
    }

    static NewsLinkerService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(NewsLinkerService.class, vertx, address);
    }

    void linkEntities(JsonObject document, Handler<AsyncResult<JsonObject>> resultHandler);
}
