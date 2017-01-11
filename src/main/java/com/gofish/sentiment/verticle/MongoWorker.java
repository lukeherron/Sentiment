package com.gofish.sentiment.verticle;


import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.ext.mongo.MongoClient;
import rx.Observable;

/**
 * @author Luke Herron
 */
public class MongoWorker extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.mongo.worker";
    private static final Logger logger = LoggerFactory.getLogger(MongoWorker.class);

    private MongoClient mongoClient;
    private MessageConsumer<Object> messageConsumer;

    @Override
    public void start() throws Exception {
        mongoClient = MongoClient.createShared(vertx, config());
        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, this::messageHandler);
        messageConsumer.exceptionHandler(exceptionHandler -> {
            exceptionHandler.printStackTrace();
            // TODO: decide how to handle receiver exceptions
        });
    }

    private void messageHandler(Message<Object> message) {
        String action = message.headers().get("action");
        JsonObject messageBody = (JsonObject) message.body();

        switch (action) {
            case "createCollection":
                createCollection(messageBody, message);
                break;
            case "createIndex":
                createIndex(messageBody, message);
                break;
            case "getCollections":
                getCollections(message);
                break;
            case "hasCollection":
                hasCollection(messageBody, message);
                break;
            case "saveArticles":
                saveArticles(messageBody, message);
                break;
            case "isIndexPresent":
                isIndexPresent(messageBody, message);
                break;
            default:
                message.reply("Invalid Action");
        }
    }

    private void createCollection(JsonObject messageBody, Message<Object> message) {
        final String collectionName = messageBody.getString("collectionName");

        logger.info("Creating collection: " + collectionName);
        hasCollection(collectionName)
                .filter(isPresent -> {
                    if (isPresent) message.fail(2, "Collection already exists");
                    return !isPresent;
                })
                .flatMap(isPresent -> mongoClient.createCollectionObservable(collectionName))
                .subscribe(
                        result -> message.reply(result),
                        failure -> message.fail(1, failure.getMessage()),
                        () -> vertx.undeploy(deploymentID())
                );
    }

    private void createIndex(JsonObject messageBody, Message<Object> message) {
        final String collectionName = messageBody.getString("collectionName");
        final String indexName = messageBody.getString("indexName");
        final JsonObject collectionIndex = messageBody.getJsonObject("collectionIndex");
        final IndexOptions indexOptions = new IndexOptions().name(indexName).unique(true);

        logger.info("Creating index: " + indexName);
        isIndexPresent(indexName, collectionName)
                .flatMap(isPresent -> isPresent ?
                        Observable.error(new Throwable("Collection already exists")) :
                        mongoClient.createIndexWithOptionsObservable(collectionName, collectionIndex, indexOptions))
                .subscribe(
                        result -> message.reply(result),
                        failure -> message.fail(1, failure.getMessage()),
                        () -> vertx.undeploy(deploymentID())
                );
    }

    private void getCollections(Message<Object> message) {
        logger.info("Retrieving collections");
        mongoClient.getCollectionsObservable().map(JsonArray::new).subscribe(
                collections -> message.reply(collections),
                failure -> message.fail(1, failure.getMessage()),
                () -> vertx.undeploy(deploymentID())
        );
    }

    private void hasCollection(JsonObject messageBody, Message message) {
        final String collectionName = messageBody.getString("collectionName");

        logger.info("Checking if collection " + collectionName + " exists");
        hasCollection(collectionName).subscribe(
                hasCollection -> message.reply(hasCollection),
                failure -> message.fail(1, failure.getMessage()),
                () -> vertx.undeploy(deploymentID())
        );
    }

    private Observable<Boolean> hasCollection(String collectionName) {
        return mongoClient.getCollectionsObservable().map(collections -> collections.contains(collectionName));
    }

    private void isIndexPresent(JsonObject messageBody, Message message) {
        final String indexName = messageBody.getString("indexName");
        final String collectionName = messageBody.getString("collectionName");

        logger.info("Checking if index " + indexName + " exists in collection " + collectionName);
        isIndexPresent(indexName, collectionName).subscribe(
                isPresent -> message.reply(isPresent),
                failure -> message.fail(1, failure.getMessage() + ":isIndexPresent"),
                () -> vertx.undeploy(deploymentID())
        );
    }

    private Observable<Boolean> isIndexPresent(String indexName, String collectionName) {
        return mongoClient.listIndexesObservable(collectionName)
                .map(indexes -> indexes.contains(indexName));
    }

    private void saveArticles(JsonObject messageBody, Message<Object> message) {
        final String collectionName = messageBody.getString("collectionName");
        final JsonArray articles = messageBody.getJsonArray("articles");

        JsonObject command = new JsonObject()
                .put("insert", collectionName)
                .put("documents", articles)
                .put("ordered", false);

        logger.info("Saving articles to collection " + collectionName);
        mongoClient.runCommandObservable("insert", command).subscribe(
                result -> message.reply(result),
                failure -> message.fail(1, failure.getMessage()),
                () -> vertx.undeploy(deploymentID())
        );
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        messageConsumer.unregisterObservable().subscribe(
                stopFuture::complete,
                stopFuture::fail,
                () -> logger.info("Unregistered message consumer for mongo worker instance")
        );
    }
}
