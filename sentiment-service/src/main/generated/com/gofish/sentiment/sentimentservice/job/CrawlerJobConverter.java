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

package com.gofish.sentiment.sentimentservice.job;

import io.vertx.core.json.JsonObject;

/**
 * Converter for {@link com.gofish.sentiment.sentimentservice.job.CrawlerJob}.
 *
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.sentimentservice.job.CrawlerJob} original class using Vert.x codegen.
 */
public class CrawlerJobConverter {

  public static void fromJson(JsonObject json, CrawlerJob obj) {
    if (json.getValue("jobResult") instanceof JsonObject) {
      obj.setResult(((JsonObject)json.getValue("jobResult")).copy());
    }
    if (json.getValue("retryStrategy") instanceof JsonObject) {
      obj.setRetryStrategy(((JsonObject)json.getValue("retryStrategy")).copy());
    }
    if (json.getValue("state") instanceof String) {
      obj.setState(com.gofish.sentiment.sentimentservice.job.Job.State.valueOf((String)json.getValue("state")));
    }
  }

  public static void toJson(CrawlerJob obj, JsonObject json) {
    json.put("attempts", obj.getAttempts());
    if (obj.getJobId() != null) {
      json.put("jobId", obj.getJobId());
    }
    if (obj.getResult() != null) {
      json.put("jobResult", obj.getResult());
    }
    if (obj.getQuery() != null) {
      json.put("query", obj.getQuery());
    }
    if (obj.getRetryStrategy() != null) {
      json.put("retryStrategy", obj.getRetryStrategy());
    }
    if (obj.getState() != null) {
      json.put("state", obj.getState().name());
    }
    json.put("timeout", obj.getTimeout());
  }
}