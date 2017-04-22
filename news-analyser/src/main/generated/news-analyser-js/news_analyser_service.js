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

/** @module news-analyser-js/news_analyser_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JNewsAnalyserService = Java.type('com.gofish.sentiment.newsanalyser.NewsAnalyserService');

/**

 @class
*/
var NewsAnalyserService = function(j_val) {

  var j_newsAnalyserService = j_val;
  var that = this;

  /**
   Processes a JSON object which must contain a 'value' JSON array of news articles, each article is analysed to
   and updated with the resulting sentiment score

   @public
   @param json {Object} JSON object containing the news articles 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   */
  this.analyseSentiment = function(json, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] === 'function') {
      j_newsAnalyserService["analyseSentiment(io.vertx.core.json.JsonObject,io.vertx.core.Handler)"](utils.convParamJsonObject(json), function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Retrieves the timeout delay which has been set on this service

   @public
   @param timeoutHandler {function} the result will be returned asynchronously in this handler 
   */
  this.getTimeout = function(timeoutHandler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_newsAnalyserService["getTimeout(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        timeoutHandler(utils.convReturnLong(ar.result()), null);
      } else {
        timeoutHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sets a timeout for this service, any calls to this service interface should respect this delay before proceeding

   @public
   @param delay {number} the delay to be set 
   */
  this.setTimeout = function(delay) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='number') {
      j_newsAnalyserService["setTimeout(java.lang.Long)"](utils.convParamLong(delay));
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_newsAnalyserService;
};

NewsAnalyserService._jclass = utils.getJavaClass("com.gofish.sentiment.newsanalyser.NewsAnalyserService");
NewsAnalyserService._jtype = {
  accept: function(obj) {
    return NewsAnalyserService._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(NewsAnalyserService.prototype, {});
    NewsAnalyserService.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
NewsAnalyserService._create = function(jdel) {
  var obj = Object.create(NewsAnalyserService.prototype, {});
  NewsAnalyserService.apply(obj, arguments);
  return obj;
}
/**
 Factory method for creating NewsAnalyserService instance

 @memberof module:news-analyser-js/news_analyser_service
 @param vertx {Vertx} Vertx instance 
 @param config {Object} JsonObject for configuring the NewsAnalyserService 
 @return {NewsAnalyserService} NewsAnalyserService object
 */
NewsAnalyserService.create = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(NewsAnalyserService, JNewsAnalyserService["create(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**
 Factory method for generating a proxy to access the NewsAnalyserService

 @memberof module:news-analyser-js/news_analyser_service
 @param vertx {Vertx} Vertx instance 
 @param address {string} The address of the news analyser service on the vertx cluster 
 @return {NewsAnalyserService} NewsAnalyserService object
 */
NewsAnalyserService.createProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(NewsAnalyserService, JNewsAnalyserService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**
 Convenience method for accessing the service name. Used primarily for the vertx generated rx version of this
 class, which does not have access to the constant declared in this interface

 @memberof module:news-analyser-js/news_analyser_service

 @return {string} String representing the name of this service
 */
NewsAnalyserService.name = function() {
  var __args = arguments;
  if (__args.length === 0) {
    return JNewsAnalyserService["name()"]();
  } else throw new TypeError('function invoked with invalid arguments');
};

/**
 Convenience method for accessing the service address. Used primarily for the vertx generated rx version of this
 class, which does not have access to the constant declared in this interface

 @memberof module:news-analyser-js/news_analyser_service

 @return {string} String representing the address of this service
 */
NewsAnalyserService.address = function() {
  var __args = arguments;
  if (__args.length === 0) {
    return JNewsAnalyserService["address()"]();
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = NewsAnalyserService;