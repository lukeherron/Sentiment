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

    /**
     * Factory method for creating NewsAnalyserService instance
     * @param vertx Vertx instance
     * @param config JsonObject for configuring the NewsAnalyserService
     * @return NewsAnalyserService object
     */
    static NewsAnalyserService create(Vertx vertx, JsonObject config) {
        return new NewsAnalyserServiceImpl(vertx, config);
    }

    /**
     * Factory method for generating a proxy to access the NewsAnalyserService
     * @param vertx Vertx instance
     * @param address The address of the news analyser service on the vertx cluster
     * @return NewsAnalyserService object
     */
    static NewsAnalyserService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(NewsAnalyserService.class, vertx, address);
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
     * Processes a JSON object which must contain a 'value' JSON array of news articles, each article is analysed to
     * and updated with the resulting sentiment score
     * @param json JSON object containing the news articles
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    void analyseSentiment(JsonObject json, Handler<AsyncResult<JsonObject>> resultHandler);

    /**
     * Retrieves the timeout delay which has been set on this service
     * @param timeoutHandler the result will be returned asynchronously in this handler
     */
    void getTimeout(Handler<AsyncResult<Long>> timeoutHandler);

    /**
     * Sets a timeout for this service, any calls to this service interface should respect this delay before proceeding
     * @param delay the delay to be set
     */
    void setTimeout(Long delay);
}
