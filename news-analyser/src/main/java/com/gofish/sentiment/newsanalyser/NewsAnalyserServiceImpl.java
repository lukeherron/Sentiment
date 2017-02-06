package com.gofish.sentiment.newsanalyser;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
public class NewsAnalyserServiceImpl implements NewsAnalyserService {

    public NewsAnalyserServiceImpl(Vertx vertx, JsonObject config) {

    }

    @Override
    public void analyseSentiment(JsonObject json, Handler<AsyncResult<JsonObject>> resultHandler) {
        
    }
}
