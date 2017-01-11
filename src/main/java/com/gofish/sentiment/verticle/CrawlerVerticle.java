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
    private static final Logger logger = LoggerFactory.getLogger(CrawlerVerticle.class);

    private CrawlerService crawlerService;
    private MessageConsumer<JsonObject> consumer;
    private int workerInstances;
    private DeploymentOptions workerOptions;

    @Override
    public void start() throws Exception {
        JsonObject crawlConfig = Optional.ofNullable(config().getJsonObject("api.news.search"))
                .orElseThrow(() -> new RuntimeException("Could not load crawler configuration"));

        crawlerService = CrawlerService.create(vertx, config());
        consumer = ProxyHelper.registerService(CrawlerService.class, vertx, crawlerService, ADDRESS);
        workerInstances = crawlConfig.getInteger("worker.instances", 2);
        workerOptions = new DeploymentOptions().setConfig(crawlConfig).setInstances(workerInstances).setWorker(true);

        int crawlFrequency = crawlConfig.getInteger("crawl.frequency", 3600000);
        vertx.setPeriodic(crawlFrequency, id -> {
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
        if (queries.size() < workerInstances) workerOptions.setInstances(queries.size());

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
                completionHandler.cause().printStackTrace(); // TODO: remove
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
                replyHandler.cause().printStackTrace(); // TODO: remove
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
