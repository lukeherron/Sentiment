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

    /**
     * Factory method for creating NewsLinkerService instance
     * @param vertx Vertx instance
     * @param config JsonObject for configuring the NewsAnalyserService
     * @return NewsAnalyserService object
     */
    static NewsLinkerService create(Vertx vertx, JsonObject config) {
        return new NewsLinkerServiceImpl(vertx, config);
    }

    /**
     * Factory method for generating a proxy to access the NewsLinkerService
     * @param vertx Vertx instance
     * @param address The address of the news analyser service on the vertx cluster
     * @return NewsAnalyserService object
     */
    static NewsLinkerService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(NewsLinkerService.class, vertx, address);
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
     * Processes a JSON object containing text which is scanned for keywords and subsequently linked to entities that
     * have a possible relation to the keywords. For example, if a news article relating to Apple Inc. is submitted, it
     * is likely to contain the word 'apple'. The entity linker processes this keyword and indicates the likelihood that
     * the article is talking about the fruit, or the Cupertino company. This should help developers and clients make
     * a determination on which articles they would like to filter out.
     * @param document
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    void linkEntities(JsonObject document, Handler<AsyncResult<JsonObject>> resultHandler);

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
