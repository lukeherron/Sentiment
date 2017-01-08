package com.gofish.sentiment.service.impl;

import com.gofish.sentiment.service.MongoService;
import com.gofish.sentiment.verticle.MongoWorker;
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
 * Implements the MongoService interface to provide a ServiceProxy API to make MongoDB requests.
 * This implementation forwards all API calls to a MongoWorker verticle via local event bus messaging. This allows us
 * to execute potentially long running DB calls inside worker verticles, but also allows us to avoid the issues with
 * attempting to pass an Rx'ified vertx instance to a service proxy, but still performing all mongo db calls with an
 * rx'ified vertx interface.
 *
 * @author Luke Herron
 */
public class MongoServiceImpl implements MongoService {

    private static final Logger logger = LoggerFactory.getLogger(MongoServiceImpl.class);

    private final EventBus eventBus;
    private final Vertx vertx;
    private final String workerAddress;
    private final DeploymentOptions workerOptions;

    private final Map<String, DeliveryOptions> deliveryOptions = new HashMap<>(6);

    public MongoServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.eventBus = vertx.eventBus();
        this.workerAddress = MongoWorker.ADDRESS;
        this.workerOptions = new DeploymentOptions().setWorker(true);

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

    private void getResult(JsonObject message, DeliveryOptions deliveryOptions, Handler<AsyncResult<Message<Object>>> replyHandler) {
        vertx.deployVerticle("com.gofish.sentiment.verticle.MongoWorker", workerOptions, completionHandler -> {
            if (completionHandler.succeeded()) {
                logger.info(deliveryOptions.getHeaders().get("action") + ": " + message.encode());
                eventBus.send(workerAddress, message, deliveryOptions, replyHandler);
            }
            else {
                logger.error(completionHandler.cause());
            }
        });
    }

    private void initDeliveryOptionsMap() {
        deliveryOptions.put("createCollection", new DeliveryOptions().addHeader("action", "createCollection"));
        deliveryOptions.put("createIndex", new DeliveryOptions().addHeader("action", "createIndex"));
        deliveryOptions.put("getCollections", new DeliveryOptions().addHeader("action", "getCollections"));
        deliveryOptions.put("hasCollection", new DeliveryOptions().addHeader("action", "hasCollection"));
        deliveryOptions.put("saveArticles", new DeliveryOptions().addHeader("action", "saveArticles"));
        deliveryOptions.put("isIndexPresent", new DeliveryOptions().addHeader("action", "isIndexPresent"));
    }
}
