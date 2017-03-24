package com.gofish.sentiment.sentimentservice;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
@ProxyGen
@VertxGen
public interface SentimentService {

    String NAME = "sentiment-eventbus-service";
    String ADDRESS = "sentiment.service";
    long SENTIMENT_PROXY_TIMEOUT = 300000; // 5 minutes

    static SentimentService create(Vertx vertx, JsonObject config) {
        return new SentimentServiceImpl(vertx, config);
    }

    static SentimentService createProxy(Vertx vertx, String address) {
        DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(SENTIMENT_PROXY_TIMEOUT);
        return ProxyHelper.createProxy(SentimentService.class, vertx, address, deliveryOptions);
    }

    @Fluent
    SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler);
}
