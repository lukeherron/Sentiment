package com.gofish.sentiment.sentimentservice.job;

import com.gofish.sentiment.sentimentservice.article.SentimentArticle;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
@DataObject(generateConverter = true)
public class LinkerJob extends AbstractJob {

    private final JsonObject article;

    public LinkerJob(SentimentArticle article) {
        // The AnalyserJobConverter class requires a constructor that accepts a JSON version of this, so unfortunately
        // we can't have another constructor to accept the native version of an article, so we accept the String version
        // instead and convert it to JsonObject inside this constructor. This has the downside of forcing clients to
        // encode the article before creating the job
        this.article = article.toJson();
    }

    public LinkerJob(JsonObject json) {
        super(json.getString("jobId"));
        article = json.getJsonObject("payload");

        LinkerJobConverter.fromJson(json, this);
    }

    @Override
    public LinkerJob copy() {
        return new LinkerJob(this.toJson().copy());
    }

    @Override
    public JsonObject getPayload() {
        return article;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        LinkerJobConverter.toJson(this, json);

        return json;
    }
}
