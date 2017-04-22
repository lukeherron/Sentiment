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
import rx.Single;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.newslinker.NewsLinkerService original} non RX-ified interface using Vert.x codegen.
 */

@io.vertx.lang.rxjava.RxGen(com.gofish.sentiment.newslinker.NewsLinkerService.class)
public class NewsLinkerService {

  public static final io.vertx.lang.rxjava.TypeArg<NewsLinkerService> __TYPE_ARG = new io.vertx.lang.rxjava.TypeArg<>(
    obj -> new NewsLinkerService((com.gofish.sentiment.newslinker.NewsLinkerService) obj),
    NewsLinkerService::getDelegate
  );

  private final com.gofish.sentiment.newslinker.NewsLinkerService delegate;
  
  public NewsLinkerService(com.gofish.sentiment.newslinker.NewsLinkerService delegate) {
    this.delegate = delegate;
  }

  public com.gofish.sentiment.newslinker.NewsLinkerService getDelegate() {
    return delegate;
  }

  /**
   * Factory method for creating NewsLinkerService instance
   * @param vertx Vertx instance
   * @param config JsonObject for configuring the NewsAnalyserService
   * @return NewsAnalyserService object
   */
  public static NewsLinkerService create(Vertx vertx, JsonObject config) { 
    NewsLinkerService ret = NewsLinkerService.newInstance(com.gofish.sentiment.newslinker.NewsLinkerService.create(vertx.getDelegate(), config));
    return ret;
  }

  /**
   * Factory method for generating a proxy to access the NewsLinkerService
   * @param vertx Vertx instance
   * @param address The address of the news analyser service on the vertx cluster
   * @return NewsAnalyserService object
   */
  public static NewsLinkerService createProxy(Vertx vertx, String address) { 
    NewsLinkerService ret = NewsLinkerService.newInstance(com.gofish.sentiment.newslinker.NewsLinkerService.createProxy(vertx.getDelegate(), address));
    return ret;
  }

  /**
   * Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the name of this service
   */
  public static String name() { 
    String ret = com.gofish.sentiment.newslinker.NewsLinkerService.name();
    return ret;
  }

  /**
   * Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the address of this service
   */
  public static String address() { 
    String ret = com.gofish.sentiment.newslinker.NewsLinkerService.address();
    return ret;
  }

  /**
   * Processes a JSON object containing text which is scanned for keywords and subsequently linked to entities that
   * have a possible relation to the keywords. For example, if a news article relating to Apple Inc. is submitted, it
   * is likely to contain the word 'apple'. The entity linker processes this keyword and indicates the likelihood that
   * the article is talking about the fruit, or the Cupertino company. This should help developers and clients make
   * a determination on which articles they would like to filter out.
   * @param document 
   * @param resultHandler the result will be returned asynchronously in this handler
   */
  public void linkEntities(JsonObject document, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.linkEntities(document, resultHandler);
  }

  /**
   * Processes a JSON object containing text which is scanned for keywords and subsequently linked to entities that
   * have a possible relation to the keywords. For example, if a news article relating to Apple Inc. is submitted, it
   * is likely to contain the word 'apple'. The entity linker processes this keyword and indicates the likelihood that
   * the article is talking about the fruit, or the Cupertino company. This should help developers and clients make
   * a determination on which articles they would like to filter out.
   * @param document 
   * @return 
   */
  public Single<JsonObject> rxLinkEntities(JsonObject document) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      linkEntities(document, fut);
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


  public static NewsLinkerService newInstance(com.gofish.sentiment.newslinker.NewsLinkerService arg) {
    return arg != null ? new NewsLinkerService(arg) : null;
  }
}
