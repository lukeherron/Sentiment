package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class AnalyserJob extends AbstractJob {

    private final JsonObject article;

    public AnalyserJob(JsonObject article) {
        this.article = article;
    }

    public JsonObject getArticle() {
        return article;
    }

    @Override
    public long getTimeout() {
        return Math.round(5L * 0.5 * (Math.pow(2, getAttempts()) - 1));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        AnalyserJobConverter.toJson(this, json); // Needs to be auto-generated first

        return json;
    }
}
