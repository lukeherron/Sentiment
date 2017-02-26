package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
public interface Job {

    @VertxGen
    enum State { INACTIVE, ACTIVE, COMPLETE, FAILED, DELAYED }

    Job copy();

    long getTimeout();

    JsonObject toJson();

    JsonObject getResult();

    void setResult(JsonObject jobResult);

    String getJobId();

    State getState();

    void setState(State state);

    int getAttempts();

    void incrementAttempts();

    JsonObject getRetryStrategy();

    void setRetryStrategy(RetryStrategy retryStrategy);

    void setRetryStrategy(JsonObject retryStrategy);

    String encode();

    String encodePrettily();
}
