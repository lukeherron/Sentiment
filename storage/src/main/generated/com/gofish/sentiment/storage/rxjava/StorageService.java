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
import rx.Single;
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

@io.vertx.lang.rxjava.RxGen(com.gofish.sentiment.storage.StorageService.class)
public class StorageService {

  public static final io.vertx.lang.rxjava.TypeArg<StorageService> __TYPE_ARG = new io.vertx.lang.rxjava.TypeArg<>(
    obj -> new StorageService((com.gofish.sentiment.storage.StorageService) obj),
    StorageService::getDelegate
  );

  private final com.gofish.sentiment.storage.StorageService delegate;
  
  public StorageService(com.gofish.sentiment.storage.StorageService delegate) {
    this.delegate = delegate;
  }

  public com.gofish.sentiment.storage.StorageService getDelegate() {
    return delegate;
  }

  public static StorageService create(Vertx vertx, JsonObject config) { 
    StorageService ret = StorageService.newInstance(com.gofish.sentiment.storage.StorageService.create(vertx.getDelegate(), config));
    return ret;
  }

  public static StorageService createProxy(Vertx vertx, String address) { 
    StorageService ret = StorageService.newInstance(com.gofish.sentiment.storage.StorageService.createProxy(vertx.getDelegate(), address));
    return ret;
  }

  public static String name() { 
    String ret = com.gofish.sentiment.storage.StorageService.name();
    return ret;
  }

  public static String address() { 
    String ret = com.gofish.sentiment.storage.StorageService.address();
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
  public Single<Void> rxCreateCollection(String collectionName) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      createCollection(collectionName, fut);
    }));
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
  public Single<Void> rxCreateIndex(String collectionName, JsonObject collectionIndex) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      createIndex(collectionName, collectionIndex, fut);
    }));
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
  public Single<JsonArray> rxGetCollections() { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      getCollections(fut);
    }));
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
  public Single<JsonObject> rxGetSentimentResults(String collectionName) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      getSentimentResults(collectionName, fut);
    }));
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
  public Single<Boolean> rxHasArticle(String collectionName, String articleName, String articleDescription) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      hasArticle(collectionName, articleName, articleDescription, fut);
    }));
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
  public Single<Boolean> rxHasCollection(String collectionName) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      hasCollection(collectionName, fut);
    }));
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
  public Single<Boolean> rxIsIndexPresent(String indexName, String collectionName) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      isIndexPresent(indexName, collectionName, fut);
    }));
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
  public Single<JsonObject> rxSaveArticles(String collectionName, JsonArray articles) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      saveArticles(collectionName, articles, fut);
    }));
  }


  public static StorageService newInstance(com.gofish.sentiment.storage.StorageService arg) {
    return arg != null ? new StorageService(arg) : null;
  }
}
