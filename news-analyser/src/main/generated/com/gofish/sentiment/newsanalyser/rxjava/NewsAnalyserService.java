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

package com.gofish.sentiment.newsanalyser.rxjava;

import java.util.Map;
import rx.Observable;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.newsanalyser.NewsAnalyserService original} non RX-ified interface using Vert.x codegen.
 */

public class NewsAnalyserService {

  final com.gofish.sentiment.newsanalyser.NewsAnalyserService delegate;

  public NewsAnalyserService(com.gofish.sentiment.newsanalyser.NewsAnalyserService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static NewsAnalyserService create(Vertx vertx, JsonObject config) { 
    NewsAnalyserService ret = NewsAnalyserService.newInstance(com.gofish.sentiment.newsanalyser.NewsAnalyserService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static NewsAnalyserService createProxy(Vertx vertx, String address) { 
    NewsAnalyserService ret = NewsAnalyserService.newInstance(com.gofish.sentiment.newsanalyser.NewsAnalyserService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  public void analyseSentiment(JsonObject json, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.analyseSentiment(json, resultHandler);
  }

  public Observable<JsonObject> analyseSentimentObservable(JsonObject json) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    analyseSentiment(json, resultHandler.toHandler());
    return resultHandler;
  }


  public static NewsAnalyserService newInstance(com.gofish.sentiment.newsanalyser.NewsAnalyserService arg) {
    return arg != null ? new NewsAnalyserService(arg) : null;
  }
}
