package com.gofish.sentiment.newsanalyser;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import rx.Single;

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

        JsonObject message = new JsonObject().put("article", article);
        vertx.eventBus().send(NewsAnalyserWorker.ADDRESS, message, handleReply(resultHandler));
    }

    @Override
    public Single<JsonObject> rxAnalyseSentiment(JsonObject article) {
        return Single.create(new SingleOnSubscribeAdapter<>(handler -> analyseSentiment(article, handler)));
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
