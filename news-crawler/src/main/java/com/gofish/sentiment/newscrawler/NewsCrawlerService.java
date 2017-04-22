package com.gofish.sentiment.newscrawler;

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
public interface NewsCrawlerService {

    String NAME = "newscrawler-eventbus-service";
    String ADDRESS = "sentiment.service.crawler";

    /**
     * Factory methods for creating NewsCrawlerService instance
     * @param vertx Vertx instance
     * @param config JsonObject for configuring the NewsCrawlerService
     * @return NewsCrawlerService object
     */
    static NewsCrawlerService create(Vertx vertx, JsonObject config) {
        return new NewsCrawlerServiceImpl(vertx, config);
    }

    /**
     * Factory method for generating a proxy to access the NewsAnalyserService
     * @param vertx Vertx instance
     * @param address The address of the news crawler service on the vertx cluster
     * @return NewsCrawlerService object
     */
    static NewsCrawlerService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(NewsCrawlerService.class, vertx, address);
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
     * Searches the news via the Bing News Search API, returning any results which are related to the supplied query
     * @param query String query which represents the news search term
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    void crawlQuery(String query, Handler<AsyncResult<JsonObject>> resultHandler);

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
