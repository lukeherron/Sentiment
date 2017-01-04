package com.gofish.sentiment.service;

import com.gofish.sentiment.service.impl.CrawlerServiceImpl;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
@ProxyGen
public interface CrawlerService {

    static CrawlerService create(Vertx vertx, JsonObject config) {
        return new CrawlerServiceImpl(vertx, config);
    }

    static CrawlerService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(CrawlerService.class, vertx, address);
    }

    @Fluent
    CrawlerService startCrawl(Handler<AsyncResult<JsonArray>> resultHandler);

    @ProxyClose
    void close();
}
