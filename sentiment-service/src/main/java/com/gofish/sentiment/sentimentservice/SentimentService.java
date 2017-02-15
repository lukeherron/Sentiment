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

    String NEWS_CRAWLER_PENDING_QUEUE = "newsCrawler:pendingQueue";
    String NEWS_CRAWLER_WORKING_QUEUE = "newsCrawler:workingQueue";
    String NEWS_ANALYSER_PENDING_QUEUE = "newsAnalyser:pendingQueue";
    String NEWS_ANALYSER_WORKING_QUEUE = "newsAnalyser:workingQueue";
    String NEWS_LINKER_PENDING_QUEUE = "newsLinker:pendingQueue";
    String NEWS_LINKER_WORKING_QUEUE = "newsLinker:workingQueue";

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

    @Fluent
    SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler);
}
