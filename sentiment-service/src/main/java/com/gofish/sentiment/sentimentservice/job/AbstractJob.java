package com.gofish.sentiment.sentimentservice.job;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.UUID;

/**
 * @author Luke Herron
 */
public abstract class AbstractJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJob.class);

    private final String jobId;
    private State state = State.INACTIVE;
    private int attempts = 0;

    private JsonObject jobResult = new JsonObject();
    private JsonObject retryStrategy = new JsonObject(); // Represented as json to ensure eventbus can transmit it

    public AbstractJob() {
        jobId = UUID.randomUUID().toString();
    }

    public abstract JsonObject toJson();

    public abstract long getTimeout();

    public JsonObject getJobResult() {
        return jobResult;
    }

    public void setJobResult(JsonObject jobResult) {
        this.jobResult = jobResult;
    }

    public String getJobId() {
        return jobId;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        attempts++;
    }

    public JsonObject getRetryStrategy() {
        return retryStrategy;
    }

    public void setRetryStrategy(JsonObject retryStrategy) {
        this.retryStrategy = retryStrategy;
    }

    public void setRetryStrategy(RetryStrategy retryStrategy) {
        setRetryStrategy(new JsonObject(Json.encodePrettily(retryStrategy)));
    }
}
