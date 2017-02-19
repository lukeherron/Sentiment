package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class CrawlerJob {

    @VertxGen
    public enum State { INACTIVE, ACTIVE, COMPLETE, FAILED, DELAYED }

    private final String jobId;
    private final String query;

    private State state = State.INACTIVE;
    private int attempts = 0;

    private JsonObject retryStrategy = new JsonObject();
    private JsonObject newsSearchResponse = new JsonObject();
    private JsonObject sentimentResponse = new JsonObject();
    private JsonObject entityLinkingResponse = new JsonObject();
    private JsonObject result = new JsonObject();

    // TODO: add metrics

    public CrawlerJob(String query) {
        this.jobId = UUID.randomUUID().toString();
        this.query = query;
    }

    public CrawlerJob(JsonObject json) {
        CrawlerJobConverter.fromJson(json, this); // needs to be auto-generated

        // JobConverter only populates fields that have a setter, so we update the rest manually
        jobId = json.getString("jobId");
        query = json.getString("query");
    }

    public JsonObject getEntityLinkingResponse() {
        return entityLinkingResponse;
    }

    public int getFailed() {
        return attempts;
    }

    public String getJobId() {
        return jobId;
    }

    public JsonObject getNewsSearchResponse() {
        return newsSearchResponse;
    }

    public String getQuery() {
        return query;
    }

    public JsonObject getResult() {
        return result;
    }

    public JsonObject getRetryStrategy() {
        return retryStrategy;
    }

    public JsonObject getSentimentResponse() {
        return sentimentResponse;
    }

    public State getState() {
        return state;
    }

    public long getTimeout() {
        return Math.round(5L * 0.5 * (Math.pow(2, attempts) - 1));
    }

    public void setEntityLinkingResponse(JsonObject entityLinkingResponse) {
        this.entityLinkingResponse = entityLinkingResponse;
    }

    public void updateFailedAttempts() {
        attempts++;
    }

    public void setNewsSearchResponse(JsonObject newsSearchResponse) {
        this.newsSearchResponse = newsSearchResponse;
    }

    public void setResult(JsonObject result) {
        this.result = result;
    }

    public void setRetryStrategy(RetryStrategy retryStrategy) {
        setRetryStrategy(new JsonObject(Json.encodePrettily(retryStrategy)));
    }

    public void setRetryStrategy(JsonObject retryStrategy) {
        this.retryStrategy = retryStrategy;
    }

    public void setSentimentResponse(JsonObject sentimentResponse) {
        this.sentimentResponse = sentimentResponse;
    }

    public void setState(State state) {
        this.state = state;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        CrawlerJobConverter.toJson(this, json); // Needs to be auto-generated first

        return json;
    }
}
