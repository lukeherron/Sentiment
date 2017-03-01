package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class CrawlerJob extends AbstractJob {

    private final String query;

    // TODO: add metrics

    public CrawlerJob(String query) {
        this.query = query;
    }

    public CrawlerJob(JsonObject json) {
        // JobConverter only populates fields that have a setter, so we update the rest manually
        super(json.getString("jobId"));
        query = json.getString("query");

        CrawlerJobConverter.fromJson(json, this); // needs to be auto-generated
    }

    public String getQuery() {
        return query;
    }

    @Override
    public CrawlerJob copy() {
        return new CrawlerJob(this.toJson().copy());
    }

//    @Override
//    public long getTimeout() {
//        return Math.round(5L * 0.5 * (Math.pow(2, getAttempts()) - 1));
//    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        CrawlerJobConverter.toJson(this, json); // Needs to be auto-generated first

        return json;
    }
}
