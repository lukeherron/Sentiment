package com.gofish.sentiment.newslinker;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
public class NewsLinkerServiceImpl implements NewsLinkerService {

    public NewsLinkerServiceImpl(Vertx vertx, JsonObject config) {

    }

    @Override
    public void linkEntities(JsonObject document, Handler<AsyncResult<JsonObject>> resultHandler) {

    }
}
