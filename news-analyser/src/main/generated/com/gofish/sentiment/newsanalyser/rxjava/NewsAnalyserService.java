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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import rx.Single;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.newsanalyser.NewsAnalyserService original} non RX-ified interface using Vert.x codegen.
 */

@io.vertx.lang.rxjava.RxGen(com.gofish.sentiment.newsanalyser.NewsAnalyserService.class)
public class NewsAnalyserService {

  public static final io.vertx.lang.rxjava.TypeArg<NewsAnalyserService> __TYPE_ARG = new io.vertx.lang.rxjava.TypeArg<>(
    obj -> new NewsAnalyserService((com.gofish.sentiment.newsanalyser.NewsAnalyserService) obj),
    NewsAnalyserService::getDelegate
  );

  private final com.gofish.sentiment.newsanalyser.NewsAnalyserService delegate;
  
  public NewsAnalyserService(com.gofish.sentiment.newsanalyser.NewsAnalyserService delegate) {
    this.delegate = delegate;
  }

  public com.gofish.sentiment.newsanalyser.NewsAnalyserService getDelegate() {
    return delegate;
  }

  /**
   * Factory method for creating NewsAnalyserService instance
   * @param vertx Vertx instance
   * @param config JsonObject for configuring the NewsAnalyserService
   * @return NewsAnalyserService object
   */
  public static NewsAnalyserService create(Vertx vertx, JsonObject config) { 
    NewsAnalyserService ret = NewsAnalyserService.newInstance(com.gofish.sentiment.newsanalyser.NewsAnalyserService.create(vertx.getDelegate(), config));
    return ret;
  }

  /**
   * Factory method for generating a proxy to access the NewsAnalyserService
   * @param vertx Vertx instance
   * @param address The address of the news analyser service on the vertx cluster
   * @return NewsAnalyserService object
   */
  public static NewsAnalyserService createProxy(Vertx vertx, String address) { 
    NewsAnalyserService ret = NewsAnalyserService.newInstance(com.gofish.sentiment.newsanalyser.NewsAnalyserService.createProxy(vertx.getDelegate(), address));
    return ret;
  }

  /**
   * Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the name of this service
   */
  public static String name() { 
    String ret = com.gofish.sentiment.newsanalyser.NewsAnalyserService.name();
    return ret;
  }

  /**
   * Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the address of this service
   */
  public static String address() { 
    String ret = com.gofish.sentiment.newsanalyser.NewsAnalyserService.address();
    return ret;
  }

  /**
   * Processes a JSON object which must contain a 'value' JSON array of news articles, each article is analysed to
   * and updated with the resulting sentiment score
   * @param json JSON object containing the news articles
   * @param resultHandler the result will be returned asynchronously in this handler
   */
  public void analyseSentiment(JsonObject json, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.analyseSentiment(json, resultHandler);
  }

  /**
   * Processes a JSON object which must contain a 'value' JSON array of news articles, each article is analysed to
   * and updated with the resulting sentiment score
   * @param json JSON object containing the news articles
   * @return 
   */
  public Single<JsonObject> rxAnalyseSentiment(JsonObject json) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      analyseSentiment(json, fut);
    }));
  }

  /**
   * Retrieves the timeout delay which has been set on this service
   * @param timeoutHandler the result will be returned asynchronously in this handler
   */
  public void getTimeout(Handler<AsyncResult<Long>> timeoutHandler) { 
    delegate.getTimeout(timeoutHandler);
  }

  /**
   * Retrieves the timeout delay which has been set on this service
   * @return 
   */
  public Single<Long> rxGetTimeout() { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      getTimeout(fut);
    }));
  }

  /**
   * Sets a timeout for this service, any calls to this service interface should respect this delay before proceeding
   * @param delay the delay to be set
   */
  public void setTimeout(Long delay) { 
    delegate.setTimeout(delay);
  }


  public static NewsAnalyserService newInstance(com.gofish.sentiment.newsanalyser.NewsAnalyserService arg) {
    return arg != null ? new NewsAnalyserService(arg) : null;
  }
}
