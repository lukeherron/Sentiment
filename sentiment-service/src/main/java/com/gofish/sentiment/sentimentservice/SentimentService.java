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

    /**
     * Factory method for creating SentimentService instance
     * @param vertx Vertx instance
     * @param config JsonObject for configuring the NewsAnalyserService
     * @return NewsAnalyserService object
     */
    static SentimentService create(Vertx vertx, JsonObject config) {
        return new SentimentServiceImpl(vertx, config);
    }

    /**
     * Factory method for generating a proxy to access the SentimentService
     * @param vertx Vertx instance
     * @param address The address of the news analyser service on the vertx cluster
     * @return NewsAnalyserService object
     */
    static SentimentService createProxy(Vertx vertx, String address) {
        DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(SENTIMENT_PROXY_TIMEOUT);
        return ProxyHelper.createProxy(SentimentService.class, vertx, address, deliveryOptions);
    }

    /**
     * Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
     * class, which does not have access to the constant declared in this interface
     * @return String representing the name of this service
     */
    static String name() {
        return NAME;
    }

    /**
     * Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
     * class, which does not have access to the constant declared in this interface
     * @return String representing the address of this service
     */
    static String address() {
        return ADDRESS;
    }

    /**
     * Retrieves the currently stored sentiment results for the supplied query
     * @param query String representing the news query to retrieve the sentiment results for
     * @param resultHandler the result will be returned asynchronously in this handler
     * @return SentimentService so this method can be used fluently
     */
    @Fluent
    SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler);

    /**
     * Crawls the news for the provided query keyword and analyses the sentiment of each news article that is found
     * @param query String representing the news query to crawl for and perform sentiment analysis on
     * @param resultHandler the result will be returned asynchronously in this handler
     * @return SentimentService so this method can be used fluently
     */
    @Fluent
    SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler);
}
