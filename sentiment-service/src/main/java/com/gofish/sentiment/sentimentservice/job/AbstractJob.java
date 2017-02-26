package com.gofish.sentiment.sentimentservice.job;

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

    AbstractJob() {
        jobId = UUID.randomUUID().toString();
    }

    AbstractJob(String jobId) {
        this.jobId = jobId;
    }

    @Override
    public JsonObject getResult() {
        return jobResult;
    }

    @Override
    public void setResult(JsonObject jobResult) {
        this.jobResult = jobResult;
    }

    @Override
    public String getJobId() {
        return jobId;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public long getTimeout() {
        if (retryStrategy == null || retryStrategy.isEmpty()) {
            return Math.round(5L * 0.5 * (Math.pow(2, attempts) - 1));
        }

        return new RetryStrategy(retryStrategy).getTimeout();
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    @Override
    public int getAttempts() {
        return attempts;
    }

    @Override
    public void incrementAttempts() {
        attempts++;
    }

    @Override
    public JsonObject getRetryStrategy() {
        return retryStrategy;
    }

    @Override
    public void setRetryStrategy(RetryStrategy retryStrategy) {
        setRetryStrategy(retryStrategy.toJson());
    }

    @Override
    public void setRetryStrategy(JsonObject retryStrategy) {
        this.retryStrategy = retryStrategy;
    }

    @Override
    public String encode() {
        return toJson().encode();
    }

    @Override
    public String encodePrettily() {
        return toJson().encodePrettily();
    }
}
