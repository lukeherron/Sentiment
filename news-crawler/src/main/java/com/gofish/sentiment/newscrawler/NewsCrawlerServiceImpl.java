package com.gofish.sentiment.newscrawler;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class NewsCrawlerServiceImpl implements NewsCrawlerService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerServiceImpl.class);

    private final Vertx vertx;
    private final DeploymentOptions workerOptions;

    public NewsCrawlerServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.workerOptions = new DeploymentOptions().setConfig(config).setWorker(true);
    }

    @Override
    public void crawlQuery(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Starting crawl for query: " + query);

        vertx.deployVerticle(NewsCrawlerWorker.ADDRESS, workerOptions, completionHandler -> {
            if (completionHandler.succeeded()) {
                JsonObject message = new JsonObject().put("query", query);
                vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, message, handleReply(resultHandler));
            }
            else {
                resultHandler.handle(Future.failedFuture(completionHandler.cause()));
            }
        });
    }

    private Handler<AsyncResult<Message<JsonObject>>> handleReply(Handler<AsyncResult<JsonObject>> resultHandler) {
        return replyHandler -> {
            if (replyHandler.succeeded()) {
                JsonObject result = replyHandler.result().body();
                resultHandler.handle(Future.succeededFuture(result));
            }
            else {
                resultHandler.handle(Future.failedFuture(replyHandler.cause()));
            }
        };
    }
}
