package com.gofish.sentiment.storage;

import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Luke Herron
 */
public class StorageServiceImpl implements StorageService {

    private static final Logger LOG = LoggerFactory.getLogger(StorageServiceImpl.class);

    private final Vertx vertx;
    private final EventBus eventBus;
    private final String workerAddress;
    private final DeploymentOptions workerOptions;

    private final Map<String, DeliveryOptions> deliveryOptions = new HashMap<>(6);

    public StorageServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.eventBus = vertx.eventBus();
        this.workerAddress = StorageWorker.ADDRESS;
        this.workerOptions = new DeploymentOptions().setConfig(config).setWorker(true);

        initDeliveryOptionsMap();
    }

    @Override
    public void createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) {
        JsonObject message = new JsonObject().put("collectionName", collectionName);

        getResult(message, deliveryOptions.get("createCollection"), handleReply(resultHandler, Void.class));
    }

    @Override
    public void createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler) {
        JsonObject message = new JsonObject()
                .put("collectionName", collectionName)
                .put("indexName", collectionName + "Index")
                .put("collectionIndex", collectionIndex);

        getResult(message, deliveryOptions.get("createIndex"), handleReply(resultHandler, Void.class));
    }

    @Override
    public void getCollections(Handler<AsyncResult<JsonArray>> resultHandler) {
        getResult(new JsonObject(), deliveryOptions.get("getCollections"), handleReply(resultHandler, JsonArray.class));
    }

    @Override
    public void getSentimentResults(String collectionName, Handler<AsyncResult<JsonObject>> resultHandler) {
        JsonObject message = new JsonObject().put("collectionName", collectionName);

        getResult(message, deliveryOptions.get("getSentimentResults"), handleReply(resultHandler, JsonObject.class));
    }

    @Override
    public void hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {
        JsonObject message = new JsonObject().put("collectionName", collectionName);

        getResult(message, deliveryOptions.get("hasCollection"), handleReply(resultHandler, Boolean.class));
    }

    @Override
    public void saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) {
        JsonObject message = new JsonObject()
                .put("collectionName", collectionName)
                .put("articles", articles);

        getResult(message, deliveryOptions.get("saveArticles"), handleReply(resultHandler, JsonObject.class));
    }

    @Override
    public void isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {
        JsonObject message = new JsonObject()
                .put("indexName", indexName)
                .put("collectionName", collectionName);

        getResult(message, deliveryOptions.get("isIndexPresent"), handleReply(resultHandler, Boolean.class));
    }

    private void getResult(JsonObject message, DeliveryOptions deliveryOptions, Handler<AsyncResult<Message<Object>>> replyHandler) {
        vertx.deployVerticle(StorageWorker.class.getName(), workerOptions, completionHandler -> {
            if (completionHandler.succeeded()) {
                LOG.info(deliveryOptions.getHeaders().get("action") + ": " + message.encode());
                eventBus.sender(workerAddress, deliveryOptions)
                        .send(message, replyHandler)
                        .exceptionHandler(cause -> replyHandler.handle(Future.failedFuture(cause)));
            }
            else {
                replyHandler.handle(Future.failedFuture(completionHandler.cause()));
            }
        });
    }

    /**
     * Default reply handler for all event bus messages that are sent to the mongo worker.
     *
     * @param resultHandler the result will be returned asynchronously in this handler via the replyHandler
     * @param type specifies the result type that the resultHandler will return

     * @return asynchronous message handler which encapsulates the result to be sent back over the event-bus
     */
    private <T> Handler<AsyncResult<Message<Object>>> handleReply(Handler<AsyncResult<T>> resultHandler, Class<T> type) {
        return replyHandler -> {
            if (replyHandler.succeeded()) {
                T result = type.cast(replyHandler.result().body());
                resultHandler.handle(Future.succeededFuture(result));
            }
            else {
                resultHandler.handle(Future.failedFuture(replyHandler.cause()));
            }
        };
    }

    /**
     * Store the delivery options for each event-bus message we wish to send, so that we are only required to instantiate
     * them once at startup, and can easily retrieve them when we wish to send specific message relating to each option
     */
    private void initDeliveryOptionsMap() {
        deliveryOptions.put("createCollection", new DeliveryOptions().addHeader("action", "createCollection"));
        deliveryOptions.put("createIndex", new DeliveryOptions().addHeader("action", "createIndex"));
        deliveryOptions.put("getCollections", new DeliveryOptions().addHeader("action", "getCollections"));
        deliveryOptions.put("getSentimentResults", new DeliveryOptions().addHeader("action", "getSentimentResults"));
        deliveryOptions.put("hasCollection", new DeliveryOptions().addHeader("action", "hasCollection"));
        deliveryOptions.put("saveArticles", new DeliveryOptions().addHeader("action", "saveArticles"));
        deliveryOptions.put("isIndexPresent", new DeliveryOptions().addHeader("action", "isIndexPresent"));
    }
}
