package com.gofish.sentiment.sentimentservice;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class SentimentJob {

    @VertxGen
    public enum State { INACTIVE, ACTIVE, COMPLETE, FAILED, DELAYED }

    private final String jobId;
    private final String query;
    private final List<JsonObject> results = Collections.synchronizedList(new ArrayList<>());

    private State state = State.INACTIVE;
    private int attempts = 0;

    // TODO: add metrics

    public SentimentJob(String query) {
        this.jobId = UUID.randomUUID().toString();
        this.query = query;
    }

    public SentimentJob(JsonObject json) {
        //SentimentJobConverter.fromJson(json, this); // needs to be auto-generated

        // JobConverter only populates fields that have a setter, so we update the rest manually
        jobId = json.getString("jobId");
        query = json.getString("query");
    }

    public int getFailed() {
        return attempts;
    }

    public String getJobId() {
        return jobId;
    }

    public String getQuery() {
        return query;
    }

    public State getState() {
        return state;
    }

    public long getTimeout() {
        return Math.round(5L * 0.5 * (Math.pow(2, attempts) - 1));
    }

    public void setFailed() {
        attempts++;
    }

    public void addResult(JsonObject result) {
        this.results.add(result);
    }

    public void setState(State state) {
        this.state = state;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        //SentimentJobConverter.toJson(this, json); // Needs to be auto-generated first

        return json;
    }
}
