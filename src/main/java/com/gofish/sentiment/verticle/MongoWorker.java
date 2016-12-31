package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.MongoService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
public class MongoWorker extends AbstractVerticle {

    static final String ADDRESS = "sentiment.mongo.worker";
    private static final Logger logger = LoggerFactory.getLogger(MongoWorker.class);

    private MongoService service;

    @Override
    public void start() throws Exception {
        service = MongoService.create(vertx, config());
        ProxyHelper.registerService(MongoService.class, vertx, service, MongoWorker.ADDRESS);
    }

    @Override
    public void stop() throws Exception {
        logger.info("Closing MongoWorker Verticle");
        service.close();
    }
}
