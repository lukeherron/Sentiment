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

package com.gofish.sentiment.rxjava.service;

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
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.service.MongoService original} non RX-ified interface using Vert.x codegen.
 */

public class MongoService {

  final com.gofish.sentiment.service.MongoService delegate;

  public MongoService(com.gofish.sentiment.service.MongoService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static MongoService create(Vertx vertx, JsonObject config) { 
    MongoService ret = MongoService.newInstance(com.gofish.sentiment.service.MongoService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static MongoService createProxy(Vertx vertx, String address) { 
    MongoService ret = MongoService.newInstance(com.gofish.sentiment.service.MongoService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  public void createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.createCollection(collectionName, resultHandler);
  }

  public Observable<Void> createCollectionObservable(String collectionName) { 
    io.vertx.rx.java.ObservableFuture<Void> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    createCollection(collectionName, resultHandler.toHandler());
    return resultHandler;
  }

  public void createIndex(String collectionName, JsonObject collectionIndex, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.createIndex(collectionName, collectionIndex, resultHandler);
  }

  public Observable<Void> createIndexObservable(String collectionName, JsonObject collectionIndex) { 
    io.vertx.rx.java.ObservableFuture<Void> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    createIndex(collectionName, collectionIndex, resultHandler.toHandler());
    return resultHandler;
  }

  public void getCollections(Handler<AsyncResult<JsonArray>> resultHandler) { 
    delegate.getCollections(resultHandler);
  }

  public Observable<JsonArray> getCollectionsObservable() { 
    io.vertx.rx.java.ObservableFuture<JsonArray> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    getCollections(resultHandler.toHandler());
    return resultHandler;
  }

  public void hasCollection(String collectionName, Handler<AsyncResult<Boolean>> resultHandler) { 
    delegate.hasCollection(collectionName, resultHandler);
  }

  public Observable<Boolean> hasCollectionObservable(String collectionName) { 
    io.vertx.rx.java.ObservableFuture<Boolean> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    hasCollection(collectionName, resultHandler.toHandler());
    return resultHandler;
  }

  public void saveArticles(String collectionName, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.saveArticles(collectionName, articles, resultHandler);
  }

  public Observable<JsonObject> saveArticlesObservable(String collectionName, JsonArray articles) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    saveArticles(collectionName, articles, resultHandler.toHandler());
    return resultHandler;
  }

  public void isIndexPresent(String indexName, String collectionName, Handler<AsyncResult<Boolean>> resultHandler) { 
    delegate.isIndexPresent(indexName, collectionName, resultHandler);
  }

  public Observable<Boolean> isIndexPresentObservable(String indexName, String collectionName) { 
    io.vertx.rx.java.ObservableFuture<Boolean> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    isIndexPresent(indexName, collectionName, resultHandler.toHandler());
    return resultHandler;
  }


  public static MongoService newInstance(com.gofish.sentiment.service.MongoService arg) {
    return arg != null ? new MongoService(arg) : null;
  }
}
