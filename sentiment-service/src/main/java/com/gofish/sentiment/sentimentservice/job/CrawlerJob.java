package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class CrawlerJob extends AbstractJob {

    private final String query;

    public CrawlerJob(String query) {
        this.query = query;
    }

    public CrawlerJob(JsonObject json) {
        // JobConverter only populates fields that have a setter, so we update the rest manually
        super(json.getString("jobId"));
        query = json.getJsonObject("payload").getString("query");

        CrawlerJobConverter.fromJson(json, this); // needs to be auto-generated
    }

    @Override
    public CrawlerJob copy() {
        return new CrawlerJob(this.toJson().copy());
    }

    @Override
    public JsonObject getPayload() {
        return new JsonObject().put("query", query);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        CrawlerJobConverter.toJson(this, json);

        return json;
    }
}
