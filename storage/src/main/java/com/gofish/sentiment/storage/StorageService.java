package com.gofish.sentiment.storage;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * @author Luke Herron
 */
@ProxyGen
@VertxGen
public interface StorageService {

    String NAME = "storage-eventbus-service";
    String ADDRESS = "sentiment.storage";

    static StorageService create(Vertx vertx, JsonObject config) {
        return new StorageServiceImpl(vertx, config);
    }

    static StorageService createProxy(Vertx vertx, String address) {
        return ProxyHelper.createProxy(StorageService.class, vertx, address);
    }

    /**
     * Create a mongo collection using the specified collection name.
     *
     * @param collectionName the name of the collection to create
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler);

    /**
     * Create a mongo index for the specified collection name. This is necessary to avoid adding duplicate documents
     * to mongo storage. Duplicate documents affect any calculations made against sentiment values.
     *
     * @param collectionName the name of the collection that the index will be created for
     * @param collectionIndex json object mapping the fields that will make up the index
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler);

    /**
     * Retrieves a list of all current collections in mongo storage
     *
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService getCollections(Handler<AsyncResult<JsonArray>> resultHandler);

    /**
     * Retrieves the sentiment results for a specific collection name. A collection name maps to an API query, so any
     * returned sentiment value will be relevant to the query that is being searched.
     *
     * @param collectionName the name of the collection that sentiment results will be retrieved from
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService getSentimentResults(String collectionName, Handler<AsyncResult<JsonObject>> resultHandler);

    /**
     * Checks if an article with a specific name and description is currently contained in mongo storage
     *
     * @param collectionName the name of the collection that the article search will be conducted within
     * @param articleName the name of the article which we are searching for
     * @param articleDescription the description of the article which we are searching for
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService hasArticle(String collectionName, String articleName, String articleDescription, Handler<AsyncResult<Boolean>> resultHandler);

    /**
     * Checks if the specified collection is currently contained in mongo storage.
     *
     * @param collectionName the name of the collection that sentiment results will be retrieved from
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler);

    /**
     * Checks if the specified index is already defined for the specified collection name
     *
     * @param indexName the name of the index to search for
     * @param collectionName the name of the collection to search
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler);

    /**
     * Stores the provided articles in the specified collection name.
     *
     * @param collectionName the name of the collection to store the articles in
     * @param articles json object containing a list of articles to store
     * @param resultHandler the result will be returned asynchronously in this handler
     */
    @Fluent
    StorageService saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler);
}
