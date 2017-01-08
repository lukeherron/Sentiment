package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.CrawlerService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
public class CrawlerVerticle extends AbstractVerticle {

    static final String ADDRESS = "sentiment.crawler";
    static final String API_BASE_URL = "api.cognitive.microsoft.com";
    static final String API_URL_PATH = "/bing/v5.0/news/search";
    static final int API_PORT = 443;
    static final int TIMER_DELAY = 3600000;
    //static final int TIMER_DELAY = 30000;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerVerticle.class);

    private MessageConsumer<JsonObject> consumer;

    @Override
    public void start() throws Exception {
        CrawlerService crawlerService = CrawlerService.create(vertx, config());
        consumer = ProxyHelper.registerService(CrawlerService.class, vertx, crawlerService, ADDRESS);
        // We have no idea how long the crawl will take to complete, but we want it to run every TIMER_DELAY interval.
        // We will need to time how long each crawl takes. If it is less than TIMER_DELAY, then we simply set a timer
        // for the time remaining. If it is greater than or equal to TIMER_DELAY, then we will want to re-launch
        // immediately (and probably warn that we need more resources for timely completion)
        // N.B. Vertx timer values must be greater than 0
        // TODO: implement functionality as discussed in above comment, probably simpler to use backpressure

        vertx.setPeriodic(TIMER_DELAY, id -> {
            crawlerService.startCrawl(resultHandler -> {
                if (resultHandler.succeeded()) {
                    logger.info(resultHandler.result());
                }
                else {
                    logger.error(resultHandler.cause());
                }
            });
        });
    }

    @Override
    public void stop() throws Exception {
        consumer.unregister();
    }
}
