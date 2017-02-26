package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class RetryStrategy {

    private final long timestamp;
    private int retryDelay;

    public RetryStrategy(JsonObject json) {
        timestamp = json.getLong("timestamp");

        RetryStrategyConverter.fromJson(json, this);
    }

    public RetryStrategy(int retryDelay) {
        //timeStamp = System.currentTimeMillis() / 1000L;
        timestamp = System.currentTimeMillis();
        this.retryDelay = retryDelay;
    }

    public long getTimeout() {
        final long timeout = retryDelay * 1000 - (System.currentTimeMillis() - timestamp);

        return timeout > 0 ? timeout : 0;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        RetryStrategyConverter.toJson(this, json);

        return json;
    }
}
