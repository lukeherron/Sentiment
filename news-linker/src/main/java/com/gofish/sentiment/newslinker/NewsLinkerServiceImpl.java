package com.gofish.sentiment.newslinker;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class NewsLinkerServiceImpl implements NewsLinkerService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerServiceImpl.class);

    private final Vertx vertx;
    private final DeploymentOptions workerOptions;

    public NewsLinkerServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.workerOptions = new DeploymentOptions().setConfig(config).setInstances(10).setWorker(true);
    }

    @Override
    public void linkEntities(JsonObject article, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Starting entity linking");

        JsonObject message = new JsonObject().put("article", article);
        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, message, handleReply(resultHandler));
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
