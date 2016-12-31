package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.MongoService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class SentimentVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SentimentVerticle.class);

    @Override
    public void start() throws Exception {
        vertx.deployVerticle(new MongoWorker(), completionHandler -> {
            MongoService mongoService = MongoService.createProxy(vertx, MongoWorker.ADDRESS);

            // An example service call to show functionality. Requires a running mongo db server.
            mongoService.hasCollection("apple", resultHandler -> {
                if (resultHandler.succeeded()) {
                    logger.info("DB has collection?: " + resultHandler.result());
                }
                else {
                    logger.error(resultHandler.cause().getMessage(), resultHandler.cause());
                }
            });
        });
    }
}
