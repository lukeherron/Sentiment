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

package com.gofish.sentiment.sentimentservice.rxjava;

import java.util.Map;
import rx.Observable;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.sentimentservice.SentimentService original} non RX-ified interface using Vert.x codegen.
 */

public class SentimentService {

  final com.gofish.sentiment.sentimentservice.SentimentService delegate;

  public SentimentService(com.gofish.sentiment.sentimentservice.SentimentService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static SentimentService create(Vertx vertx, JsonObject config) { 
    SentimentService ret = SentimentService.newInstance(com.gofish.sentiment.sentimentservice.SentimentService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static SentimentService createProxy(Vertx vertx, String address) { 
    SentimentService ret = SentimentService.newInstance(com.gofish.sentiment.sentimentservice.SentimentService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  public SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.getSentiment(query, resultHandler);
    return this;
  }

  public Observable<JsonObject> getSentimentObservable(String query) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    getSentiment(query, resultHandler.toHandler());
    return resultHandler;
  }


  public static SentimentService newInstance(com.gofish.sentiment.sentimentservice.SentimentService arg) {
    return arg != null ? new SentimentService(arg) : null;
  }
}
