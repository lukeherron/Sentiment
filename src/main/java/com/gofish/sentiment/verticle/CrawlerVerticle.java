package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.CrawlerService;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
public class CrawlerVerticle extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.crawler";
    public static final int TIMER_DELAY = 3600000;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerVerticle.class);

    private CrawlerService service;
    private MessageConsumer<JsonObject> consumer;

    @Override
    public void start() throws Exception {
        // We have no idea how long the crawl will take to complete, but we want it to run every TIMER_DELAY interval.
        // We will need to time how long each crawl takes. If it is less than TIMER_DELAY, then we simply set a timer
        // for the time remaining. If it is greater than or equal to TIMER_DELAY, then we will want to re-launch
        // immediately (and probably warn that we need more resources for timely completion)
        // N.B. Vertx timer values must be greater than 0
        // TODO: implement functionality as discussed in above comment
        service = CrawlerService.create(getVertx(), config());
        consumer = ProxyHelper.registerService(CrawlerService.class, getVertx(), service, ADDRESS);
    }

    @Override
    public void stop() throws Exception {
        consumer.unregister();
        service.close();
    }
}
