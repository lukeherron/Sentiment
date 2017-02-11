package com.gofish.sentiment.sentimentservice;

import io.vertx.codegen.annotations.Fluent;
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
public interface SentimentService {

    String NEWSCRAWLER_PENDING = "newsCrawler:pendingQueue";
    String NEWSCRAWLER_WORKING = "newsCrawler:workingQueue";
    String SENTIMENT_PENDING = "newsAnalyser:pendingQueue";
    String SENTIMENT_WORKING = "newsAnalyser:workingQueue";
    String ENTITYLINK_PENDING = "newsLinker:pendingQueue";
    String ENTITYLINK_WORKING = "newsLinker:workingQueue";

    String NAME = "sentiment-eventbus-service";
    String ADDRESS = "sentiment.service";

    static SentimentService create(Vertx vertx, JsonObject config) {
        return new SentimentServiceImpl(vertx, config);
    }

    static SentimentService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(SentimentService.class, vertx, address);
    }

    @Fluent
    SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler);
}
