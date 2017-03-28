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

/** @module storage-js/storage_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JStorageService = Java.type('com.gofish.sentiment.storage.StorageService');

/**

 @class
*/
var StorageService = function(j_val) {

  var j_storageService = j_val;
  var that = this;

  /**
   Create a mongo collection using the specified collection name.

   @public
   @param collectionName {string} the name of the collection to create 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.createCollection = function(collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_storageService["createCollection(java.lang.String,io.vertx.core.Handler)"](collectionName, function(ar) {
      if (ar.succeeded()) {
        resultHandler(null, null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Create a mongo index for the specified collection name. This is necessary to avoid adding duplicate documents
   to mongo storage. Duplicate documents affect any calculations made against sentiment values.

   @public
   @param collectionName {string} the name of the collection that the index will be created for 
   @param collectionIndex {Object} json object mapping the fields that will make up the index 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.createIndex = function(collectionName, collectionIndex, resultHandler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && (typeof __args[1] === 'object' && __args[1] != null) && typeof __args[2] === 'function') {
      j_storageService["createIndex(java.lang.String,io.vertx.core.json.JsonObject,io.vertx.core.Handler)"](collectionName, utils.convParamJsonObject(collectionIndex), function(ar) {
      if (ar.succeeded()) {
        resultHandler(null, null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Retrieves a list of all current collections in mongo storage

   @public
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.getCollections = function(resultHandler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_storageService["getCollections(io.vertx.core.Handler)"](function(ar) {
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
   Retrieves the sentiment results for a specific collection name. A collection name maps to an API query, so any
   returned sentiment value will be relevant to the query that is being searched.

   @public
   @param collectionName {string} the name of the collection that sentiment results will be retrieved from 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.getSentimentResults = function(collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_storageService["getSentimentResults(java.lang.String,io.vertx.core.Handler)"](collectionName, function(ar) {
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
   Checks if an article with a specific name and description is currently contained in mongo storage

   @public
   @param collectionName {string} the name of the collection that the article search will be conducted within 
   @param articleName {string} the name of the article which we are searching for 
   @param articleDescription {string} the description of the article which we are searching for 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.hasArticle = function(collectionName, articleName, articleDescription, resultHandler) {
    var __args = arguments;
    if (__args.length === 4 && typeof __args[0] === 'string' && typeof __args[1] === 'string' && typeof __args[2] === 'string' && typeof __args[3] === 'function') {
      j_storageService["hasArticle(java.lang.String,java.lang.String,java.lang.String,io.vertx.core.Handler)"](collectionName, articleName, articleDescription, function(ar) {
      if (ar.succeeded()) {
        resultHandler(ar.result(), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Checks if the specified collection is currently contained in mongo storage.

   @public
   @param collectionName {string} the name of the collection that sentiment results will be retrieved from 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.hasCollection = function(collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_storageService["hasCollection(java.lang.String,io.vertx.core.Handler)"](collectionName, function(ar) {
      if (ar.succeeded()) {
        resultHandler(ar.result(), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Checks if the specified index is already defined for the specified collection name

   @public
   @param indexName {string} the name of the index to search for 
   @param collectionName {string} the name of the collection to search 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.isIndexPresent = function(indexName, collectionName, resultHandler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'string' && typeof __args[2] === 'function') {
      j_storageService["isIndexPresent(java.lang.String,java.lang.String,io.vertx.core.Handler)"](indexName, collectionName, function(ar) {
      if (ar.succeeded()) {
        resultHandler(ar.result(), null);
      } else {
        resultHandler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Stores the provided articles in the specified collection name.

   @public
   @param collectionName {string} the name of the collection to store the articles in 
   @param articles {todo} json object containing a list of articles to store 
   @param resultHandler {function} the result will be returned asynchronously in this handler 
   @return {StorageService}
   */
  this.saveArticles = function(collectionName, articles, resultHandler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'object' && __args[1] instanceof Array && typeof __args[2] === 'function') {
      j_storageService["saveArticles(java.lang.String,io.vertx.core.json.JsonArray,io.vertx.core.Handler)"](collectionName, utils.convParamJsonArray(articles), function(ar) {
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
  this._jdel = j_storageService;
};

StorageService._jclass = utils.getJavaClass("com.gofish.sentiment.storage.StorageService");
StorageService._jtype = {
  accept: function(obj) {
    return StorageService._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(StorageService.prototype, {});
    StorageService.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
StorageService._create = function(jdel) {
  var obj = Object.create(StorageService.prototype, {});
  StorageService.apply(obj, arguments);
  return obj;
}
/**

 @memberof module:storage-js/storage_service
 @param vertx {Vertx} 
 @param config {Object} 
 @return {StorageService}
 */
StorageService.create = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(StorageService, JStorageService["create(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)));
  } else throw new TypeError('function invoked with invalid arguments');
};

/**

 @memberof module:storage-js/storage_service
 @param vertx {Vertx} 
 @param address {string} 
 @return {StorageService}
 */
StorageService.createProxy = function(vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(StorageService, JStorageService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address));
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = StorageService;