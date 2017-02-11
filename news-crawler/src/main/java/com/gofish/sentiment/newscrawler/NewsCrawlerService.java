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

    static NewsCrawlerService create(Vertx vertx, JsonObject config) {
        return new NewsCrawlerServiceImpl(vertx, config);
    }

    static NewsCrawlerService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(NewsCrawlerService.class, vertx, address);
    }

    void crawlQuery(String query, Handler<AsyncResult<JsonObject>> resultHandler);
}
