package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.CrawlerService;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Luke Herron
 */
public class CrawlerVerticle extends AbstractVerticle {

    static final String ADDRESS = "sentiment.crawler";
    static final String API_BASE_URL = "api.cognitive.microsoft.com";
    static final String API_URL_PATH = "/bing/v5.0/news/search";
    static final int API_PORT = 443;
    static final int TIMER_DELAY = 3600000;
    //static final int TIMER_DELAY = 5000;
    private static final int WORKER_INSTANCES = 2;
    private static final Logger logger = LoggerFactory.getLogger(CrawlerVerticle.class);

    private CrawlerService crawlerService;
    private MessageConsumer<JsonObject> consumer;
    private DeploymentOptions workerOptions;

    @Override
    public void start() throws Exception {
        crawlerService = CrawlerService.create(vertx, config());
        consumer = ProxyHelper.registerService(CrawlerService.class, vertx, crawlerService, ADDRESS);
        workerOptions = new DeploymentOptions().setConfig(config()).setInstances(WORKER_INSTANCES);
        // We have no idea how long the crawl will take to complete, but we want it to run every TIMER_DELAY interval.
        // We will need to time how long each crawl takes. If it is less than TIMER_DELAY, then we simply set a timer
        // for the time remaining. If it is greater than or equal to TIMER_DELAY, then we will want to re-launch
        // immediately (and probably warn that we need more resources for timely completion)
        // N.B. Vertx timer values must be greater than 0
        // TODO: implement functionality as discussed in above comment, probably simpler to use backpressure

        vertx.setPeriodic(TIMER_DELAY, id -> {
            crawlerService.getQueries(resultHandler -> {
                if (resultHandler.succeeded()) {
                    JsonArray queries = resultHandler.result();
                    if (!queries.isEmpty()) crawlQueries(queries);
                }
                else {
                    resultHandler.cause().printStackTrace();
                    logger.error(resultHandler.cause());
                }
            });
        });
    }

    private void crawlQueries(JsonArray queries) {
        if (queries.size() < WORKER_INSTANCES) workerOptions.setInstances(queries.size());

        vertx.deployVerticle("com.gofish.sentiment.verticle.CrawlerWorker", workerOptions, completionHandler -> {
            AtomicInteger completedQueries = new AtomicInteger(0);
            if (completionHandler.succeeded()) {
                queries.forEach(query -> crawlQuery(query.toString(), resultHandler -> {
                    // Undeploy worker verticles once all queries have been processed.
                    if (completedQueries.incrementAndGet() == queries.size()) {
                        vertx.undeploy(completionHandler.result());
                    }
                }));
            }
            else {
                completionHandler.cause().printStackTrace();
                logger.error(completionHandler.cause());
            }
        });
    }

    private void crawlQuery(String query, Handler<AsyncResult<Void>> resultHandler) {
        JsonObject message = new JsonObject().put("query", query);
        DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "crawlQuery");
        vertx.eventBus().send(CrawlerWorker.ADDRESS, message, deliveryOptions, replyHandler -> {
            if (replyHandler.succeeded()) {
                JsonObject result = Optional.ofNullable(replyHandler.result().body())
                        .map(body -> (JsonObject) body)
                        .orElse(new JsonObject().put("failed", "No response returned for query"));

                saveResult(query, result);
                logger.info(result);
                resultHandler.handle(Future.succeededFuture());
            }
            else {
                replyHandler.cause().printStackTrace();
                logger.error(replyHandler.cause());
                resultHandler.handle(Future.failedFuture(replyHandler.cause()));
            }
        });
    }

    private void saveResult(String query, JsonObject result) {
        Optional.ofNullable(result.getJsonArray("value")).ifPresent(articles -> {
            crawlerService.saveArticles(query, articles, resultHandler -> {
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
