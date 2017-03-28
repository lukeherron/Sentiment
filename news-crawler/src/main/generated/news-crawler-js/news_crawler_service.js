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

/** @module news-crawler-js/news_crawler_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JNewsCrawlerService = Java.type('com.gofish.sentiment.newscrawler.NewsCrawlerService');

/**

 @class
*/
var NewsCrawlerService = function(j_val) {

  var j_newsCrawlerService = j_val;
  var that = this;

  /**

   @public
   @param query {string} 
   @param resultHandler {function} 
   */
  this.crawlQuery = function(query, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_newsCrawlerService["crawlQuery(java.lang.String,io.vertx.core.Handler)"](query, function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_newsCrawlerService;
};

NewsCrawlerService._jclass = utils.getJavaClass("com.gofish.sentiment.newscrawler.NewsCrawlerService");
NewsCrawlerService._jtype = {
  accept: function(obj) {
    return NewsCrawlerService._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(NewsCrawlerService.prototype, {});
    NewsCrawlerService.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
NewsCrawlerService._create = function(jdel) {
  var obj = Object.create(NewsCrawlerService.prototype, {});
  NewsCrawlerService.apply(obj, arguments);
  return obj;
}
/**

 @memberof module:news-crawler-js/news_crawler_service
 @param vertx {Vertx} 
 @param config {Object} 
 @return {NewsCrawlerService}
 */
NewsCrawlerService.create = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(NewsCrawlerService, JNewsCrawlerService["create(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**

 @memberof module:news-crawler-js/news_crawler_service
 @param vertx {Vertx} 
 @param address {string} 
 @return {NewsCrawlerService}
 */
NewsCrawlerService.createProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(NewsCrawlerService, JNewsCrawlerService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address));
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = NewsCrawlerService;