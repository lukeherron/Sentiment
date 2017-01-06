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

/** @module sentiment-root-module-js/mongo_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMongoService = com.gofish.sentiment.service.MongoService;

/**

 @class
*/
var MongoService = function(j_val) {

  var j_mongoService = j_val;
  var that = this;

  /**

   @public
   @param collectionName {string} 
   @param resultHandler {function} 
   */
  this.createCollection = function(collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_mongoService["createCollection(java.lang.String,io.vertx.core.Handler)"](collectionName, function(ar) {
      if (ar.succeeded()) {
        resultHandler(null, null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param collectionName {string} 
   @param collectionIndex {Object} 
   @param resultHandler {function} 
   */
  this.createIndex = function(collectionName, collectionIndex, resultHandler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && (typeof __args[1] === 'object' && __args[1] != null) && typeof __args[2] === 'function') {
      j_mongoService["createIndex(java.lang.String,io.vertx.core.json.JsonObject,io.vertx.core.Handler)"](collectionName, utils.convParamJsonObject(collectionIndex), function(ar) {
      if (ar.succeeded()) {
        resultHandler(null, null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param resultHandler {function} 
   */
  this.getCollections = function(resultHandler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mongoService["getCollections(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param collectionName {string} 
   @param resultHandler {function} 
   */
  this.hasCollection = function(collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_mongoService["hasCollection(java.lang.String,io.vertx.core.Handler)"](collectionName, function(ar) {
      if (ar.succeeded()) {
        resultHandler(ar.result(), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param collectionName {string} 
   @param articles {todo} 
   @param resultHandler {function} 
   */
  this.saveArticles = function(collectionName, articles, resultHandler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'object' && __args[1] instanceof Array && typeof __args[2] === 'function') {
      j_mongoService["saveArticles(java.lang.String,io.vertx.core.json.JsonArray,io.vertx.core.Handler)"](collectionName, utils.convParamJsonArray(articles), function(ar) {
      if (ar.succeeded()) {
        resultHandler(utils.convReturnJson(ar.result()), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param indexName {string} 
   @param collectionName {string} 
   @param resultHandler {function} 
   */
  this.isIndexPresent = function(indexName, collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'string' && typeof __args[2] === 'function') {
      j_mongoService["isIndexPresent(java.lang.String,java.lang.String,io.vertx.core.Handler)"](indexName, collectionName, function(ar) {
      if (ar.succeeded()) {
        resultHandler(ar.result(), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mongoService;
};

/**

 @memberof module:sentiment-root-module-js/mongo_service
 @param vertx {Vertx} 
 @param config {Object} 
 @return {MongoService}
 */
MongoService.create = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(JMongoService["create(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)), MongoService);
  } else throw new TypeError('function invoked with invalid arguments');
};

/**

 @memberof module:sentiment-root-module-js/mongo_service
 @param vertx {Vertx} 
 @param address {string} 
 @return {MongoService}
 */
MongoService.createProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(JMongoService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address), MongoService);
  } else throw new TypeError('function invoked with invalid arguments');
};

// We export the Constructor function
module.exports = MongoService;