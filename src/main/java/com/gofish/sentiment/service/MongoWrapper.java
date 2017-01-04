package com.gofish.sentiment.service;


import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.mongo.MongoClient;
import rx.Observable;

/**
 * @author Luke Herron
 */
public class MongoWrapper {

    private static final String INDEX_NAME_SUFFIX = "Index";
    private static final Logger logger = LoggerFactory.getLogger(MongoWrapper.class);

    private final MongoClient mongoClient;

    public MongoWrapper(Vertx vertx, JsonObject config) {
        this.mongoClient = MongoClient.createShared(vertx, config);
    }

    protected Observable<Void> createCollection(String collectionName) {
        return hasCollection(collectionName)
                .filter(isPresent -> !isPresent)
                .flatMap(isPresent -> mongoClient.createCollectionObservable(collectionName));
    }

    protected Observable<Void> createIndex(String collectioName, JsonObject collectionIndex) {
        String indexName = collectioName + INDEX_NAME_SUFFIX;

        return isIndexPresent(indexName, collectioName)
                .filter(isPresent -> isPresent)
                .flatMap(isPresent -> {
                    IndexOptions indexOptions = new IndexOptions().name(indexName).unique(true);
                    return mongoClient.createIndexWithOptionsObservable(collectioName, collectionIndex, indexOptions);
                });
    }

    protected Observable<JsonArray> getCollections() {
        return mongoClient.getCollectionsObservable().map(JsonArray::new);
    }

    protected Observable<Boolean> hasCollection(String collectionName) {
        return mongoClient.getCollectionsObservable().contains(collectionName);
    }

    protected Observable<JsonObject> saveArticles(String collectionName, JsonArray articles) {
        // TODO: do we need to consider the fact that news articles can be updated?
        JsonObject command = new JsonObject()
                .put("insert", collectionName)
                .put("document", articles)
                .put("ordered", false);

        return mongoClient.runCommandObservable("insert", command);
    }

    protected Observable<Boolean> isIndexPresent(String indexName, String collectionName) {
        return mongoClient.listIndexesObservable(collectionName).contains(indexName);
    }

    protected void close() {
        mongoClient.close();
    }
}
