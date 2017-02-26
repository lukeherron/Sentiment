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
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link com.gofish.sentiment.sentimentservice.job.RetryStrategy}.
 *
 * NOTE: This class has been automatically generated from the {@link com.gofish.sentiment.sentimentservice.job.RetryStrategy} original class using Vert.x codegen.
 */
public class RetryStrategyConverter {

  public static void fromJson(JsonObject json, RetryStrategy obj) {
    if (json.getValue("retryDelay") instanceof Number) {
      obj.setRetryDelay(((Number)json.getValue("retryDelay")).intValue());
    }
  }

  public static void toJson(RetryStrategy obj, JsonObject json) {
    json.put("retryDelay", obj.getRetryDelay());
    json.put("timeout", obj.getTimeout());
    json.put("timestamp", obj.getTimestamp());
  }
}