package com.gofish.sentiment.newsanalyser;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class NewsAnalyserServiceImpl implements NewsAnalyserService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsAnalyserServiceImpl.class);

    private final Vertx vertx;
    private final DeploymentOptions workerOptions;

    public NewsAnalyserServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.workerOptions = new DeploymentOptions().setConfig(config).setWorker(true);
    }

    @Override
    public void analyseSentiment(JsonObject article, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Starting sentiment analysis");

        vertx.deployVerticle(NewsAnalyserWorker.class.getName(), workerOptions, completionHandler -> {
            if (completionHandler.succeeded()) {
//                eventBus.sender(NewsAnalyserWorker.ADDRESS)
//                        .send(new JsonObject().put("article", article), handleReply(resultHandler))
//                        .exceptionHandler(cause -> resultHandler.handle(Future.failedFuture(cause)));
                JsonObject message = new JsonObject().put("article", article);
                vertx.eventBus().send(NewsAnalyserWorker.ADDRESS, message, handleReply(resultHandler));
            }
            else {
                LOG.error(completionHandler.cause().getMessage(), completionHandler.cause());
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
                LOG.error(replyHandler.cause().getMessage(), replyHandler.cause());
                resultHandler.handle(Future.failedFuture(replyHandler.cause()));
            }
        };
    }
}
