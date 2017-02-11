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

package com.gofish.sentiment.newscrawler.rxjava;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import rx.Observable;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.newscrawler.NewsCrawlerService original} non RX-ified interface using Vert.x codegen.
 */

public class NewsCrawlerService {

  final com.gofish.sentiment.newscrawler.NewsCrawlerService delegate;

  public NewsCrawlerService(com.gofish.sentiment.newscrawler.NewsCrawlerService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static NewsCrawlerService create(Vertx vertx, JsonObject config) { 
    NewsCrawlerService ret = NewsCrawlerService.newInstance(com.gofish.sentiment.newscrawler.NewsCrawlerService.create((io.vertx.core.Vertx)vertx.getDelegate(), config));
    return ret;
  }

  public static NewsCrawlerService createProxy(Vertx vertx, String address) { 
    NewsCrawlerService ret = NewsCrawlerService.newInstance(com.gofish.sentiment.newscrawler.NewsCrawlerService.createProxy((io.vertx.core.Vertx)vertx.getDelegate(), address));
    return ret;
  }

  public void crawlQuery(String query, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.crawlQuery(query, resultHandler);
  }

  public Observable<JsonObject> crawlQueryObservable(String query) { 
    io.vertx.rx.java.ObservableFuture<JsonObject> resultHandler = io.vertx.rx.java.RxHelper.observableFuture();
    crawlQuery(query, resultHandler.toHandler());
    return resultHandler;
  }


  public static NewsCrawlerService newInstance(com.gofish.sentiment.newscrawler.NewsCrawlerService arg) {
    return arg != null ? new NewsCrawlerService(arg) : null;
  }
}
