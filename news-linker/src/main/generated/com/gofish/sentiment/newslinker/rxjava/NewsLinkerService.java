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

package com.gofish.sentiment.newslinker.rxjava;

import java.util.Map;
import rx.Observable;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.newslinker.NewsLinkerService original} non RX-ified interface using Vert.x codegen.
 */

public class NewsLinkerService {

  final com.gofish.sentiment.newslinker.NewsLinkerService delegate;

  public NewsLinkerService(com.gofish.sentiment.newslinker.NewsLinkerService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static NewsLinkerService create(Vertx vertx, JsonObject config) { 
    NewsLinkerService ret = NewsLinkerService.newInstance(com.gofish.sentiment.newslinker.NewsLinkerService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static NewsLinkerService createProxy(Vertx vertx, String address) { 
    NewsLinkerService ret = NewsLinkerService.newInstance(com.gofish.sentiment.newslinker.NewsLinkerService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  public void linkEntities(JsonObject document, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.linkEntities(document, resultHandler);
  }

  public Observable<JsonObject> linkEntitiesObservable(JsonObject document) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    linkEntities(document, resultHandler.toHandler());
    return resultHandler;
  }


  public static NewsLinkerService newInstance(com.gofish.sentiment.newslinker.NewsLinkerService arg) {
    return arg != null ? new NewsLinkerService(arg) : null;
  }
}
