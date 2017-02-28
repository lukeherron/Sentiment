package com.gofish.sentiment.storage;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.ext.mongo.MongoClient;
import rx.Observable;

/**
 * @author Luke Herron
 */
public class StorageWorker extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.storage.worker";
    private static final Logger LOG = LoggerFactory.getLogger(StorageWorker.class);

    private MongoClient mongo;
    private MessageConsumer<JsonObject> messageConsumer;

    public StorageWorker() {
        // Vert-x requires default constructor
    }

    public StorageWorker(MongoClient mongo) {
        this.mongo = mongo;
    }

    @Override
    public void start() throws Exception {
        mongo = mongo == null ? MongoClient.createShared(vertx, config()) : mongo;
        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, this::messageHandler);
    }

    /**
     * We follow the vertx convention for the event bus message format. A header called 'action' gives the name of the
     * function to be performed, and the body of the message should be a json object, which contains the message (if any).
     * We utilise this convertion to switch on the 'action' header and perform the related task.
     *
     * @param message json object which contains both the message header and the message itself
     *
     * @see <a href="https://github.com/vert-x3/vertx-service-proxy/blob/master/src/main/asciidoc/java/index.adoc#convention-for-invoking-services-over-the-event-bus-without-proxies">
     *     event-bus message format convention
     *     </a>
     */
    private void messageHandler(Message<JsonObject> message) {
        String action = message.headers().get("action");

        switch (action) {
            case "createCollection":
                createCollection(message.body(), message);
                break;
            case "createIndex":
                createIndex(message.body(), message);
                break;
            case "getCollections":
                getCollections(message);
                break;
            case "getSentimentResults":
                getSentimentResults(message.body(), message);
                break;
            case "hasArticle":
                hasArticle(message.body(), message);
                break;
            case "hasCollection":
                hasCollection(message.body(), message);
                break;
            case "saveArticles":
                saveArticles(message.body(), message);
                break;
            case "isIndexPresent":
                isIndexPresent(message.body(), message);
                break;
            default:
                message.reply("Invalid Action");
        }
    }

    /**
     * Create a mongo collection using the name specified within the messageBody json object
     *
     * @param messageBody the jsonObject which holds the collection name
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void createCollection(JsonObject messageBody, Message<JsonObject> message) {
        final String collectionName = messageBody.getString("collectionName");

        LOG.info("Creating collection: " + collectionName);

        hasCollection(collectionName)
                .filter(isPresent -> {
                    if (isPresent) message.fail(2, "Collection already exists");
                    return !isPresent;
                })
                .flatMap(isPresent -> mongo.createCollectionObservable(collectionName))
                .subscribe(
                        result -> message.reply(result),
                        failure -> message.fail(1, failure.getMessage()),
                        () -> vertx.undeploy(deploymentID())
                );
    }

    /**
     * Create a mongo index for the specified collection name. This is necessary to avoid adding duplicate documents
     * to mongo storage. Duplicate documents affect any calculations made against sentiment values.
     *
     * @param messageBody the jsonObject which holds the collection name and index name
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void createIndex(JsonObject messageBody, Message<JsonObject> message) {
        final String collectionName = messageBody.getString("collectionName");
        final String indexName = messageBody.getString("indexName");
        final JsonObject collectionIndex = messageBody.getJsonObject("collectionIndex");
        final IndexOptions indexOptions = new IndexOptions().name(indexName).unique(true);

        LOG.info("Creating index: " + indexName);

        isIndexPresent(indexName, collectionName)
                .flatMap(isPresent -> isPresent ?
                        Observable.error(new Throwable("Index already exists")) :
                        mongo.createIndexWithOptionsObservable(collectionName, collectionIndex, indexOptions))
                .subscribe(
                        result -> message.reply(result),
                        failure -> message.fail(1, failure.getMessage()),
                        () -> vertx.undeploy(deploymentID())
                );
    }

    /**
     * Retrieves a list of all current collections in mongo storage
     *
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void getCollections(Message<JsonObject> message) {
        LOG.info("Retrieving collections");

        mongo.getCollectionsObservable().map(JsonArray::new).subscribe(
                collections -> message.reply(collections),
                failure -> message.fail(1, failure.getMessage()),
                () -> vertx.undeploy(deploymentID())
        );
    }

    /**
     * Retrieves the sentiment results for a specific collection name. A collection name maps to an API query, so any
     * returned sentiment value will be relevant to the query that is being searched.
     *
     * @param messageBody the jsonObject which holds the collection name
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void getSentimentResults(JsonObject messageBody, Message<JsonObject> message) {
        final String collectionName = messageBody.getString("collectionName");

        LOG.info("Retrieving sentiment results");

        JsonObject command = new JsonObject()
                .put("aggregate", collectionName)
                .put("pipeline", new JsonArray()
                    .add(new JsonObject().put("$group", new JsonObject()
                            .put("_id", "")
                            .put("score", new JsonObject().put("$avg", "$sentiment.score"))))
                    .add(new JsonObject().put("$project", new JsonObject().put("_id", 0).put("score", 1)))
                );

        mongo.runCommandObservable("aggregate", command)
                .map(response -> response.getJsonArray("result", new JsonArray()))
                .map(json -> json.isEmpty() ? new JsonObject() : json.getJsonObject(0))
                .subscribe(
                        result -> message.reply(result),
                        failure -> message.fail(1, failure.getMessage()),
                        () -> vertx.undeploy(deploymentID())
                );
    }

    private void hasArticle(JsonObject messageBody, Message<JsonObject> message) {
        final String collectionName = messageBody.getString("collectionName");
        final JsonObject findQuery = messageBody.getJsonObject("findQuery");
        final FindOptions findOptions = new FindOptions().setFields(new JsonObject().put("_id", 1)).setLimit(1);

        LOG.info("Checking if collection " + collectionName + " contains article");

        mongo.findWithOptionsObservable(collectionName, findQuery, findOptions).isEmpty().subscribe(
                hasArticle -> message.reply(hasArticle),
                failure -> message.fail(1, failure.getMessage()),
                () -> vertx.undeploy(deploymentID())
        );
    }

    /**
     * Checks if the specified collection is currently contained in mongo storage.
     *
     * @param messageBody the jsonObject which holds the collection name
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void hasCollection(JsonObject messageBody, Message<JsonObject> message) {
        final String collectionName = messageBody.getString("collectionName");

        LOG.info("Checking if collection " + collectionName + " exists");

        hasCollection(collectionName).subscribe(
                hasCollection -> message.reply(hasCollection),
                failure -> message.fail(1, failure.getMessage()),
                () -> vertx.undeploy(deploymentID())
        );
    }

    /**
     * Checks if the specified collection is currently contained in mongo storage
     *
     * @param collectionName the collection name to search for
     * @return observable which emits the results of the search
     */
    private Observable<Boolean> hasCollection(String collectionName) {
        return mongo.getCollectionsObservable().map(collections -> collections.contains(collectionName));
    }

    /**
     * Checks if the specified index is already defined for the specified collection name
     *
     * @param messageBody the jsonObject which holds the collection name
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void isIndexPresent(JsonObject messageBody, Message<JsonObject> message) {
        final String indexName = messageBody.getString("indexName");
        final String collectionName = messageBody.getString("collectionName");

        LOG.info("Checking if index " + indexName + " exists in collection " + collectionName);

        isIndexPresent(indexName, collectionName).subscribe(
                isPresent -> message.reply(isPresent),
                failure -> message.fail(1, failure.getMessage() + ":isIndexPresent"),
                () -> vertx.undeploy(deploymentID())
        );
    }

    /**
     * Checks if the specified index is already defined for the specified collection name
     *
     * @param indexName index name to search for
     * @param collectionName collection name to search within
     * @return observable which emits the results of the search
     */
    private Observable<Boolean> isIndexPresent(String indexName, String collectionName) {
        return mongo.listIndexesObservable(collectionName)
                .flatMap(Observable::from)
                .map(index -> ((JsonObject) index).getString("name").equals(indexName))
                .takeUntil(nameExists -> nameExists)
                .lastOrDefault(false);
    }

    /**
     * Stores the provided articles in the specified collection name.
     *
     * @param messageBody the jsonObject which holds the collection name
     * @param message the originating message, which the result will be sent to via message reply
     */
    private void saveArticles(JsonObject messageBody, Message<JsonObject> message) {
        final String collectionName = messageBody.getString("collectionName");
        final JsonArray articles = messageBody.getJsonArray("articles");

        JsonObject command = new JsonObject()
                .put("insert", collectionName)
                .put("documents", articles)
                .put("ordered", false);

        LOG.info("Saving articles to collection " + collectionName);

        mongo.runCommandObservable("insert", command).subscribe(
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
                () -> LOG.info("Unregistered message consumer for mongo worker instance")
        );
    }
}