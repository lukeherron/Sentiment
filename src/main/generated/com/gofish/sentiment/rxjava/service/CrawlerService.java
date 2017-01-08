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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import rx.Observable;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.service.CrawlerService original} non RX-ified interface using Vert.x codegen.
 */

public class CrawlerService {

  final com.gofish.sentiment.service.CrawlerService delegate;

  public CrawlerService(com.gofish.sentiment.service.CrawlerService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static CrawlerService create(Vertx vertx, JsonObject config) { 
    CrawlerService ret = CrawlerService.newInstance(com.gofish.sentiment.service.CrawlerService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static CrawlerService createProxy(Vertx vertx, String address) { 
    CrawlerService ret = CrawlerService.newInstance(com.gofish.sentiment.service.CrawlerService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  public void addNewQuery(String query, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.addNewQuery(query, resultHandler);
  }

  public Observable<Void> addNewQueryObservable(String query) { 
    io.vertx.rx.java.ObservableFuture<Void> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    addNewQuery(query, resultHandler.toHandler());
    return resultHandler;
  }

  public void getQueries(Handler<AsyncResult<JsonArray>> resultHandler) { 
    delegate.getQueries(resultHandler);
  }

  public Observable<JsonArray> getQueriesObservable() { 
    io.vertx.rx.java.ObservableFuture<JsonArray> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    getQueries(resultHandler.toHandler());
    return resultHandler;
  }

  public void isQueryActive(String query, Handler<AsyncResult<Boolean>> resultHandler) { 
    delegate.isQueryActive(query, resultHandler);
  }

  public Observable<Boolean> isQueryActiveObservable(String query) { 
    io.vertx.rx.java.ObservableFuture<Boolean> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    isQueryActive(query, resultHandler.toHandler());
    return resultHandler;
  }

  public void saveArticles(String query, JsonArray articles, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.saveArticles(query, articles, resultHandler);
  }

  public Observable<JsonObject> saveArticlesObservable(String query, JsonArray articles) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    saveArticles(query, articles, resultHandler.toHandler());
    return resultHandler;
  }


  public static CrawlerService newInstance(com.gofish.sentiment.service.CrawlerService arg) {
    return arg != null ? new CrawlerService(arg) : null;
  }
}
