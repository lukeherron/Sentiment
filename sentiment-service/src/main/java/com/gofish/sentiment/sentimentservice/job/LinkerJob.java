package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class LinkerJob extends AbstractJob {

    private final JsonObject article;

    public LinkerJob(JsonObject article) {
        this.article = article;
    }

    public JsonObject getArticle() {
        return article;
    }

    @Override
    public long getTimeout() {
        return 0;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        LinkerJobConverter.toJson(this, json); // Needs to be auto-generated first

        return json;
    }
}
