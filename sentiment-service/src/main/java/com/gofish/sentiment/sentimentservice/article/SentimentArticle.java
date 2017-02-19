package com.gofish.sentiment.sentimentservice.article;

import io.vertx.core.json.JsonObject;

/**
 * @author Luke Herron
 */
public class SentimentArticle {

    private final JsonObject article;

    public SentimentArticle(JsonObject article) {
        this.article = article;
    }

    public JsonObject toJson() {
        return article;
    }

    public String getUUID() {
        return article.getString("SentimentUUID");
    }
}
