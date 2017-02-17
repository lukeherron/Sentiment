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

package com.gofish.sentiment.sentimentservice;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link com.gofish.sentiment.sentimentservice.SentimentJob}.
 *
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.sentimentservice.SentimentJob} original class using Vert.x codegen.
 */
public class SentimentJobConverter {

  public static void fromJson(JsonObject json, SentimentJob obj) {
    if (json.getValue("entityLinkingResponse") instanceof JsonObject) {
      obj.setEntityLinkingResponse(((JsonObject)json.getValue("entityLinkingResponse")).copy());
    }
    if (json.getValue("newsSearchResponse") instanceof JsonObject) {
      obj.setNewsSearchResponse(((JsonObject)json.getValue("newsSearchResponse")).copy());
    }
    if (json.getValue("result") instanceof JsonObject) {
      obj.setResult(((JsonObject)json.getValue("result")).copy());
    }
    if (json.getValue("sentimentResponse") instanceof JsonObject) {
      obj.setSentimentResponse(((JsonObject)json.getValue("sentimentResponse")).copy());
    }
    if (json.getValue("state") instanceof String) {
      obj.setState(com.gofish.sentiment.sentimentservice.SentimentJob.State.valueOf((String)json.getValue("state")));
    }
  }

  public static void toJson(SentimentJob obj, JsonObject json) {
    if (obj.getEntityLinkingResponse() != null) {
      json.put("entityLinkingResponse", obj.getEntityLinkingResponse());
    }
    json.put("failed", obj.getFailed());
    if (obj.getJobId() != null) {
      json.put("jobId", obj.getJobId());
    }
    if (obj.getNewsSearchResponse() != null) {
      json.put("newsSearchResponse", obj.getNewsSearchResponse());
    }
    if (obj.getQuery() != null) {
      json.put("query", obj.getQuery());
    }
    if (obj.getResult() != null) {
      json.put("result", obj.getResult());
    }
    if (obj.getSentimentResponse() != null) {
      json.put("sentimentResponse", obj.getSentimentResponse());
    }
    if (obj.getState() != null) {
      json.put("state", obj.getState().name());
    }
    json.put("timeout", obj.getTimeout());
  }
}