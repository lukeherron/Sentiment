package com.gofish.sentiment.service;

import com.gofish.sentiment.service.impl.SentimentServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
public interface SentimentService {

    static SentimentService create(Vertx vertx, JsonObject config) {
        return new SentimentServiceImpl(vertx, config);
    }

    static SentimentService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(SentimentService.class, vertx, address);
    }
}
