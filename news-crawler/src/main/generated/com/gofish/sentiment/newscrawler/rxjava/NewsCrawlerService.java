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
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.newscrawler.NewsCrawlerService original} non RX-ified interface using Vert.x codegen.
 */

@io.vertx.lang.rxjava.RxGen(com.gofish.sentiment.newscrawler.NewsCrawlerService.class)
public class NewsCrawlerService {

  public static final io.vertx.lang.rxjava.TypeArg<NewsCrawlerService> __TYPE_ARG = new io.vertx.lang.rxjava.TypeArg<>(
    obj -> new NewsCrawlerService((com.gofish.sentiment.newscrawler.NewsCrawlerService) obj),
    NewsCrawlerService::getDelegate
  );

  private final com.gofish.sentiment.newscrawler.NewsCrawlerService delegate;
  
  public NewsCrawlerService(com.gofish.sentiment.newscrawler.NewsCrawlerService delegate) {
    this.delegate = delegate;
  }

  public com.gofish.sentiment.newscrawler.NewsCrawlerService getDelegate() {
    return delegate;
  }

  /**
   * Factory methods for creating NewsCrawlerService instance
   * @param vertx Vertx instance
   * @param config JsonObject for configuring the NewsCrawlerService
   * @return NewsCrawlerService object
   */
  public static NewsCrawlerService create(Vertx vertx, JsonObject config) { 
    NewsCrawlerService ret = NewsCrawlerService.newInstance(com.gofish.sentiment.newscrawler.NewsCrawlerService.create(vertx.getDelegate(), config));
    return ret;
  }

  /**
   * Factory method for generating a proxy to access the NewsAnalyserService
   * @param vertx Vertx instance
   * @param address The address of the news crawler service on the vertx cluster
   * @return NewsCrawlerService object
   */
  public static NewsCrawlerService createProxy(Vertx vertx, String address) { 
    NewsCrawlerService ret = NewsCrawlerService.newInstance(com.gofish.sentiment.newscrawler.NewsCrawlerService.createProxy(vertx.getDelegate(), address));
    return ret;
  }

  /**
   * Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the name of this service
   */
  public static String name() { 
    String ret = com.gofish.sentiment.newscrawler.NewsCrawlerService.name();
    return ret;
  }

  /**
   * Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
   * class, which does not have access to the constant declared in this interface
   * @return String representing the address of this service
   */
  public static String address() { 
    String ret = com.gofish.sentiment.newscrawler.NewsCrawlerService.address();
    return ret;
  }

  /**
   * Searches the news via the Bing News Search API, returning any results which are related to the supplied query
   * @param query String query which represents the news search term
   * @param resultHandler the result will be returned asynchronously in this handler
   */
  public void crawlQuery(String query, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.crawlQuery(query, resultHandler);
  }

  /**
   * Searches the news via the Bing News Search API, returning any results which are related to the supplied query
   * @param query String query which represents the news search term
   * @return 
   */
  public Single<JsonObject> rxCrawlQuery(String query) { 
    return Single.create(new io.vertx.rx.java.SingleOnSubscribeAdapter<>(fut -> {
      crawlQuery(query, fut);
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


  public static NewsCrawlerService newInstance(com.gofish.sentiment.newscrawler.NewsCrawlerService arg) {
    return arg != null ? new NewsCrawlerService(arg) : null;
  }
}
