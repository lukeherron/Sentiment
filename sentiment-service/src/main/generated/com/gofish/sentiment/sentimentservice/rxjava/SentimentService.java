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
import rx.Single;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.sentimentservice.SentimentService original} non RX-ified interface using Vert.x codegen.
 */

@io.vertx.lang.rxjava.RxGen(com.gofish.sentiment.sentimentservice.SentimentService.class)
public class SentimentService {

  public static final io.vertx.lang.rxjava.TypeArg<SentimentService> __TYPE_ARG = new io.vertx.lang.rxjava.TypeArg<>(
    obj -> new SentimentService((com.gofish.sentiment.sentimentservice.SentimentService) obj),
    SentimentService::getDelegate
  );

  private final com.gofish.sentiment.sentimentservice.SentimentService delegate;
  
  public SentimentService(com.gofish.sentiment.sentimentservice.SentimentService delegate) {
    this.delegate = delegate;
  }

  public com.gofish.sentiment.sentimentservice.SentimentService getDelegate() {
    return delegate;
  }

  /**
   * Factory method for creating SentimentService instance
   * @param vertx Vertx instance
   * @param config JsonObject for configuring the NewsAnalyserService
   * @return NewsAnalyserService object
   */
  public static SentimentService create(Vertx vertx, JsonObject config) { 
    SentimentService ret = SentimentService.newInstance(com.gofish.sentiment.sentimentservice.SentimentService.create(vertx.getDelegate(), config));
    return ret;
  }

  /**
   * Factory method for generating a proxy to access the SentimentService
   * @param vertx Vertx instance
   * @param address The address of the news analyser service on the vertx cluster
   * @return NewsAnalyserService object
   */
  public static SentimentService createProxy(Vertx vertx, String address) { 
    SentimentService ret = SentimentService.newInstance(com.gofish.sentiment.sentimentservice.SentimentService.createProxy(vertx.getDelegate(), address));
    return ret;
  }

  /**
   * Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the name of this service
   */
  public static String name() { 
    String ret = com.gofish.sentiment.sentimentservice.SentimentService.name();
    return ret;
  }

  /**
   * Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the address of this service
   */
  public static String address() { 
    String ret = com.gofish.sentiment.sentimentservice.SentimentService.address();
    return ret;
  }

  /**
   * Retrieves the currently stored sentiment results for the supplied query
   * @param query String representing the news query to retrieve the sentiment results for
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return SentimentService so this method can be used fluently
   */
  public SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.getSentiment(query, resultHandler);
    return this;
  }

  /**
   * Retrieves the currently stored sentiment results for the supplied query
   * @param query String representing the news query to retrieve the sentiment results for
   * @return 
   */
  public Single<JsonObject> rxGetSentiment(String query) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      getSentiment(query, fut);
    }));
  }

  /**
   * Crawls the news for the provided query keyword and analyses the sentiment of each news article that is found
   * @param query String representing the news query to crawl for and perform sentiment analysis on
   * @param resultHandler the result will be returned asynchronously in this handler
   * @return SentimentService so this method can be used fluently
   */
  public SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.analyseSentiment(query, resultHandler);
    return this;
  }

  /**
   * Crawls the news for the provided query keyword and analyses the sentiment of each news article that is found
   * @param query String representing the news query to crawl for and perform sentiment analysis on
   * @return 
   */
  public Single<JsonObject> rxAnalyseSentiment(String query) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      analyseSentiment(query, fut);
    }));
  }


  public static SentimentService newInstance(com.gofish.sentiment.sentimentservice.SentimentService arg) {
    return arg != null ? new SentimentService(arg) : null;
  }
}
