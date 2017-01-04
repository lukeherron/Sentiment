package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.MongoService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
public class MongoVerticle extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.mongo";
    private static final Logger logger = LoggerFactory.getLogger(MongoVerticle.class);

    private MongoService service;
    private MessageConsumer<JsonObject> consumer;

    @Override
    public void start() throws Exception {
        service = MongoService.create(vertx, config());
        consumer = ProxyHelper.registerService(MongoService.class, vertx, service, ADDRESS);
    }

    @Override
    public void stop() throws Exception {
        consumer.unregister();
        service.close();
    }
}
