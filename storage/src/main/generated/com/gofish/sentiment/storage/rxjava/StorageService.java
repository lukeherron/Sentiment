/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.gofish.sentiment.storage.rxjava;

import java.util.Map;
import rx.Observable;
import io.vertx.core.json.JsonArray;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.storage.StorageService original} non RX-ified interface using Vert.x codegen.
 */

public class StorageService {

  final com.gofish.sentiment.storage.StorageService delegate;

  public StorageService(com.gofish.sentiment.storage.StorageService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static StorageService create(Vertx vertx, JsonObject config) { 
    StorageService ret = StorageService.newInstance(com.gofish.sentiment.storage.StorageService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static StorageService createProxy(Vertx vertx, String address) { 
    StorageService ret = StorageService.newInstance(com.gofish.sentiment.storage.StorageService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  /**
   * Create a mongo collection using the specified collection name.
   * @param collectionName the name of the collection to create
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.createCollection(collectionName, resultHandler);
    return this;
  }

  /**
   * Create a mongo collection using the specified collection name.
   * @param collectionName the name of the collection to create
   * @return 
   */
  public Observable<Void> createCollectionObservable(String collectionName) { 
    io.vertx.rx.java.ObservableFuture<Void> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    createCollection(collectionName, resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Create a mongo index for the specified collection name. This is necessary to avoid adding duplicate documents
   * to mongo storage. Duplicate documents affect any calculations made against sentiment values.
   * @param collectionName the name of the collection that the index will be created for
   * @param collectionIndex json object mapping the fields that will make up the index
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.createIndex(collectionName, collectionIndex, resultHandler);
    return this;
  }

  /**
   * Create a mongo index for the specified collection name. This is necessary to avoid adding duplicate documents
   * to mongo storage. Duplicate documents affect any calculations made against sentiment values.
   * @param collectionName the name of the collection that the index will be created for
   * @param collectionIndex json object mapping the fields that will make up the index
   * @return 
   */
  public Observable<Void> createIndexObservable(String collectionName, JsonObject collectionIndex) { 
    io.vertx.rx.java.ObservableFuture<Void> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    createIndex(collectionName, collectionIndex, resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Retrieves a list of all current collections in mongo storage
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService getCollections(Handler<AsyncResult<JsonArray>> resultHandler) { 
    delegate.getCollections(resultHandler);
    return this;
  }

  /**
   * Retrieves a list of all current collections in mongo storage
   * @return 
   */
  public Observable<JsonArray> getCollectionsObservable() { 
    io.vertx.rx.java.ObservableFuture<JsonArray> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    getCollections(resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Retrieves the sentiment results for a specific collection name. A collection name maps to an API query, so any
   * returned sentiment value will be relevant to the query that is being searched.
   * @param collectionName the name of the collection that sentiment results will be retrieved from
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService getSentimentResults(String collectionName, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.getSentimentResults(collectionName, resultHandler);
    return this;
  }

  /**
   * Retrieves the sentiment results for a specific collection name. A collection name maps to an API query, so any
   * returned sentiment value will be relevant to the query that is being searched.
   * @param collectionName the name of the collection that sentiment results will be retrieved from
   * @return 
   */
  public Observable<JsonObject> getSentimentResultsObservable(String collectionName) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    getSentimentResults(collectionName, resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Checks if an article with a specific name and description is currently contained in mongo storage
   * @param collectionName the name of the collection that the article search will be conducted within
   * @param articleName the name of the article which we are searching for
   * @param articleDescription the description of the article which we are searching for
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService hasArticle(String collectionName, String articleName, String articleDescription, Handler<AsyncResult<Boolean>> resultHandler) { 
    delegate.hasArticle(collectionName, articleName, articleDescription, resultHandler);
    return this;
  }

  /**
   * Checks if an article with a specific name and description is currently contained in mongo storage
   * @param collectionName the name of the collection that the article search will be conducted within
   * @param articleName the name of the article which we are searching for
   * @param articleDescription the description of the article which we are searching for
   * @return 
   */
  public Observable<Boolean> hasArticleObservable(String collectionName, String articleName, String articleDescription) { 
    io.vertx.rx.java.ObservableFuture<Boolean> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    hasArticle(collectionName, articleName, articleDescription, resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Checks if the specified collection is currently contained in mongo storage.
   * @param collectionName the name of the collection that sentiment results will be retrieved from
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler) { 
    delegate.hasCollection(collectionName, resultHandler);
    return this;
  }

  /**
   * Checks if the specified collection is currently contained in mongo storage.
   * @param collectionName the name of the collection that sentiment results will be retrieved from
   * @return 
   */
  public Observable<Boolean> hasCollectionObservable(String collectionName) { 
    io.vertx.rx.java.ObservableFuture<Boolean> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    hasCollection(collectionName, resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Checks if the specified index is already defined for the specified collection name
   * @param indexName the name of the index to search for
   * @param collectionName the name of the collection to search
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler) { 
    delegate.isIndexPresent(indexName, collectionName, resultHandler);
    return this;
  }

  /**
   * Checks if the specified index is already defined for the specified collection name
   * @param indexName the name of the index to search for
   * @param collectionName the name of the collection to search
   * @return 
   */
  public Observable<Boolean> isIndexPresentObservable(String indexName, String collectionName) { 
    io.vertx.rx.java.ObservableFuture<Boolean> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    isIndexPresent(indexName, collectionName, resultHandler.toHandler());
    return resultHandler;
  }

  /**
   * Stores the provided articles in the specified collection name.
   * @param collectionName the name of the collection to store the articles in
   * @param articles json object containing a list of articles to store
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return 
   */
  public StorageService saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.saveArticles(collectionName, articles, resultHandler);
    return this;
  }

  /**
   * Stores the provided articles in the specified collection name.
   * @param collectionName the name of the collection to store the articles in
   * @param articles json object containing a list of articles to store
   * @return 
   */
  public Observable<JsonObject> saveArticlesObservable(String collectionName, JsonArray articles) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    saveArticles(collectionName, articles, resultHandler.toHandler());
    return resultHandler;
  }


  public static StorageService newInstance(com.gofish.sentiment.storage.StorageService arg) {
    return arg != null ? new StorageService(arg) : null;
  }
}
