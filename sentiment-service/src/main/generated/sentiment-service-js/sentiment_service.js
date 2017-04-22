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

/** @module sentiment-service-js/sentiment_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JSentimentService = Java.type('com.gofish.sentiment.sentimentservice.SentimentService');

/**

 @class
*/
var SentimentService = function(j_val) {

  var j_sentimentService = j_val;
  var that = this;

  /**
   Retrieves the currently stored sentiment results for the supplied query

   @public
   @param query {string} String representing the news query to retrieve the sentiment results for 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {SentimentService} SentimentService so this method can be used fluently
   */
  this.getSentiment = function(query, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_sentimentService["getSentiment(java.lang.String,io.vertx.core.Handler)"](query, function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Crawls the news for the provided query keyword and analyses the sentiment of each news article that is found

   @public
   @param query {string} String representing the news query to crawl for and perform sentiment analysis on 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {SentimentService} SentimentService so this method can be used fluently
   */
  this.analyseSentiment = function(query, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_sentimentService["analyseSentiment(java.lang.String,io.vertx.core.Handler)"](query, function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_sentimentService;
};

SentimentService._jclass = utils.getJavaClass("com.gofish.sentiment.sentimentservice.SentimentService");
SentimentService._jtype = {
  accept: function(obj) {
    return SentimentService._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(SentimentService.prototype, {});
    SentimentService.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
SentimentService._create = function(jdel) {
  var obj = Object.create(SentimentService.prototype, {});
  SentimentService.apply(obj, arguments);
  return obj;
}
/**
 Factory method for creating SentimentService instance

 @memberof module:sentiment-service-js/sentiment_service
 @param vertx {Vertx} Vertx instance 
 @param config {Object} JsonObject for configuring the NewsAnalyserService 
 @return {SentimentService} NewsAnalyserService object
 */
SentimentService.create = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(SentimentService, JSentimentService["create(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**
 Factory method for generating a proxy to access the SentimentService

 @memberof module:sentiment-service-js/sentiment_service
 @param vertx {Vertx} Vertx instance 
 @param address {string} The address of the news analyser service on the vertx cluster 
 @return {SentimentService} NewsAnalyserService object
 */
SentimentService.createProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(SentimentService, JSentimentService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**
 Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
 class, which does not have access to the constant declared in this interface

 @memberof module:sentiment-service-js/sentiment_service

 @return {string} String representing the name of this service
 */
SentimentService.name = function() {
  var __args = arguments;
  if (__args.length === 0) {
    return JSentimentService["name()"]();
  } else throw new TypeError('function invoked with invalid arguments');
};

/**
 Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
 class, which does not have access to the constant declared in this interface

 @memberof module:sentiment-service-js/sentiment_service

 @return {string} String representing the address of this service
 */
SentimentService.address = function() {
  var __args = arguments;
  if (__args.length === 0) {
    return JSentimentService["address()"]();
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = SentimentService;