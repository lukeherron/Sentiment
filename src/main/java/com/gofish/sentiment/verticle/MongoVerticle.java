package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.MongoService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
public class MongoVerticle extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.mongo";

    private MessageConsumer<JsonObject> consumer;
    private MongoClient mongoClient;

    @Override
    public void start() throws Exception {
        MongoService mongoService = MongoService.create(vertx, config());
        consumer = ProxyHelper.registerService(MongoService.class, vertx, mongoService, ADDRESS);
        mongoClient = MongoClient.createShared(vertx, config());
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        mongoClient.close();
        consumer.unregister(completionHandler -> stopFuture.complete());
    }
}
