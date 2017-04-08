package com.gofish.sentiment.storage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import rx.Observable;
import rx.Single;

import java.util.List;

/**
 * @author Luke Herron
 */
public class StorageServiceImpl implements StorageService {

    private static final Logger LOG = LoggerFactory.getLogger(StorageServiceImpl.class);

    private final MongoClient mongo;

    public StorageServiceImpl(Vertx vertx, JsonObject config) {
        this.mongo = MongoClient.createShared(vertx, config);
    }

    @Override
    public StorageService createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) {
        LOG.info("Creating collection '" + collectionName + "'");

        rxHasCollection(collectionName)
                .flatMap(isPresent -> isPresent ?
                        Single.error(new Throwable("Collection already exists")) :
                        rxCreateCollection(collectionName))
                .subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Create a mongo collection using the name specified within the messageBody json object
     * @param collectionName name of the collection that is to be created
     * @return Single which emits the result of creating the collection
     */
    private Single<Void> rxCreateCollection(String collectionName) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut -> mongo.createCollection(collectionName, fut)));
    }

    @Override
    public StorageService createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler) {
        final String indexName = collectionName + "Index";
        final IndexOptions indexOptions = new IndexOptions().name(indexName).unique(true);

        LOG.info("Creating index '" + indexName + "'");

        rxIsIndexPresent(indexName, collectionName)
                .flatMap(isPresent -> isPresent ?
                        Single.error(new Throwable("Index already exists")) :
                        rxCreateIndex(collectionName, collectionIndex, indexOptions))
                .subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Create a mongo index for the specified collection name. This is necessary to avoid adding duplicate documents
     * to mongo storage. Duplicate documents affect any calculations made against sentiment values.
     * @param collectionName name of the collection for which the index will be created
     * @param collectionIndex JsonObject which specifies the fields that form the index
     * @param indexOptions Options used to configure the index
     * @return Single which emits the result of index creation
     */
    private Single<Void> rxCreateIndex(String collectionName, JsonObject collectionIndex, IndexOptions indexOptions) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut ->
                mongo.createIndexWithOptions(collectionName, collectionIndex, indexOptions, fut)));
    }

    @Override
    public StorageService getCollections(Handler<AsyncResult<JsonArray>> resultHandler) {
        LOG.info("Retrieving collections");

        rxGetCollections().map(JsonArray::new).subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Retrieves a list of all current collections in mongo storage
     * @return Single which emits all available collection found in mongo storage
     */
    private Single<List<String>> rxGetCollections() {

        return Single.create(new SingleOnSubscribeAdapter<>(mongo::getCollections));
    }

    @Override
    public StorageService getSentimentResults(String collectionName, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Retrieving sentiment results for collection '" + collectionName + "'");

        final JsonObject getResultsCommand = new JsonObject()
                .put("aggregate", collectionName)
                .put("pipeline", new JsonArray()
                        .add(new JsonObject().put("$group", new JsonObject()
                                .put("_id", "")
                                .put("score", new JsonObject().put("$avg", "$sentiment.score"))))
                        .add(new JsonObject().put("$project", new JsonObject().put("_id", 0).put("score", 1))));

        rxGetSentimentResults(getResultsCommand).subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Retrieves the sentiment results for a specific collection name. A collection name maps to an API query, so any
     * returned sentiment value will be relevant to the query that is being searched.
     * @param getResultsCommand the JsonObject which describes the mongo command used to retrieve the results
     * @return Single which emits the results of the command
     */
    private Single<JsonObject> rxGetSentimentResults(JsonObject getResultsCommand) {

        return Single.create(new SingleOnSubscribeAdapter<JsonObject>(fut ->
                mongo.runCommand("aggregate", getResultsCommand, fut)))
                .map(response -> response.getJsonArray("result", new JsonArray()))
                .map(json -> json.isEmpty() ? new JsonObject() : json.getJsonObject(0));
    }

    @Override
    public StorageService hasArticle(String collectionName, String articleName, String articleDescription, Handler<AsyncResult<Boolean>> resultHandler) {
        LOG.info("Checking if '" + collectionName + "' has article '" + articleName + "'");

        final JsonObject findQuery = new JsonObject().put("name", articleName).put("description", articleDescription);
        final FindOptions findOptions = new FindOptions().setFields(new JsonObject().put("_id", 1)).setLimit(1);

        rxHasArticle(collectionName, findQuery, findOptions).subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Checks if the specified article exists within the specified collection name
     * @param collectionName the collection name to search for the article within
     * @param findQuery the find query which defines the article name and description to search for
     * @param findOptions findOptions which determines the return output
     * @return Single which emits the results of the search
     */
    private Single<Boolean> rxHasArticle(String collectionName, JsonObject findQuery, FindOptions findOptions) {

        return Single.create(new SingleOnSubscribeAdapter<List<JsonObject>>(fut ->
                mongo.findWithOptions(collectionName, findQuery, findOptions, fut)))
                .map(collections -> !collections.isEmpty());
    }

    @Override
    public StorageService hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {
        LOG.info("Checking if collection '" + collectionName + "' exists");

        rxHasCollection(collectionName).subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Checks if the specified collection is currently contained in mongo storage
     * @param collectionName the collection name to search for
     * @return Single which emits the results of the search
     */
    private Single<Boolean> rxHasCollection(String collectionName) {

        return rxGetCollections().map(collections -> collections.contains(collectionName));
    }

    @Override
    public StorageService isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler) {
        LOG.info("Checking if index '" + indexName + "' exists in collection '" + collectionName + "'");

        rxIsIndexPresent(indexName, collectionName).subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Checks if the specified index is already defined for the specified collection name
     * @param indexName index name to search for
     * @param collectionName collection name to search within
     * @return Single which emits the result of the search
     */
    private Single<Boolean> rxIsIndexPresent(String indexName, String collectionName) {

        return Single.create(new SingleOnSubscribeAdapter<JsonArray>(fut -> mongo.listIndexes(collectionName, fut)))
                .flatMapObservable(Observable::from)
                .map(index -> ((JsonObject) index).getString("name").equals(indexName))
                .firstOrDefault(false, equals -> equals)
                .toSingle();
    }

    @Override
    public StorageService saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Saving articles to collection '" + collectionName + "'");

        final JsonObject command = new JsonObject()
                .put("insert", collectionName)
                .put("documents", articles)
                .put("ordered", false);

        rxSaveArticles(command).subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Stores the provided articles in the specified collection name.
     * @param saveArticlesCommand the JsonObject which describes the mongo command used to insert documents
     * @return Single which emits the result of the save command
     */
    private Single<JsonObject> rxSaveArticles(JsonObject saveArticlesCommand) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut -> mongo.runCommand("insert", saveArticlesCommand, fut)));
    }
}
