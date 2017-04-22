package com.gofish.sentiment.newscrawler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * @author Luke Herron
 */
public class ResponseParser {

    /**
     *  Parses a response JSON object from Sentiment's NewsCrawlerWorker and returns a JSON object formatted to the
     *  requirements of this module. This consists of extracting nested 'related' articles, and 'de-nesting' i.e.
     *  moving them up to become parent nodes in the JSON structure. This is largely done to simply storage once
     *  article are persisted, and to simplify calculating the sentiment for each article within the persistence layer.
     *
     * @param response JSON response received from NewsCrawlerWorker
     * @return formatted JSON response
     */
    public static JsonObject parse(JsonObject response) {
        if (!response.containsKey("value")) {
            // We didn't receive the expected results, throw an exception with the response as the exception message
            throw new RuntimeException(response.encode());
        }

        JsonObject copy = response.copy(); // We will be changing the structure of the json, so make a copy to change.
        JsonArray articles = copy.getJsonArray("value").copy();

        assignUUID(articles);

        // Our json object can contain nested json representing 'associated' articles (referred to as clusteredArticles
        // by the API). We want these articles to be stored as their own entry in the DB, so we extract them into a
        // separate array and remove the nested values from the original json object (i.e. the copy of the original)
        JsonArray associatedArticles = extractAssociatedArticles(articles);

        if (associatedArticles.size() > 0) {
            articles.addAll(associatedArticles);
        }

        copy.getJsonArray("value").clear().addAll(articles);

        return copy;
    }

    /**
     * Assign each article a unique ID so that we can more easily compare articles.
     *
     * @param articles JsonArray containing the articles to assign UUID's to
     */
    private static void assignUUID(JsonArray articles) {
        articles.stream().parallel()
                .map(article -> (JsonObject) article)
                .forEach(article -> article.put("sentimentUUID", UUID.randomUUID().toString()));
    }

    /**
     * Extracts the nested article, if any, from the parent article
     *
     * @param articles JsonArray of article entries
     * @return JsonArray of article entries, flattened such that no articles are nested inside others.
     */
    private static JsonArray extractAssociatedArticles(JsonArray articles) {
        JsonArray associatedArticles = new JsonArray();

        // For each article in the json response, we check to see if there are any clustered articles. We want to rank
        // these articles as well, but we don't want them nested inside other articles. We essentially are going to
        // 'flatten' the clustered articles by removing them as nested objects and placing them as root articles. We
        // start with the intermediary step of extracting them to a seperate JsonArray
        articles.stream().map(article -> (JsonObject) article)
                .filter(article -> article.containsKey("clusteredArticles"))
                .forEach(article -> {
                    // Generally clustered articles don't duplicate the entity context (i.e. "about" section values) of
                    // the parent article. We don't want to lose this info when we move the clustered article, so we
                    // ensure that we copy across any missing info first.
                    try {
                        preserveEntityContext(associatedArticles, article);
                    }
                    catch (Throwable t) {
                        t.printStackTrace();
                    }
                    article.remove("clusteredArticles");
                });

        return associatedArticles;
    }

    /**
     * Preserves any links which may exist in a parent article but do not exist, and are relevant, to any nested
     * articles. This is to ensure that no information is lost once the articles have been de-nested.
     *
     * @param associatedArticles JsonArray of article entries containing entity links.
     * @param article JsonObject indicating the article to which any entity copies needs to be made to.
     */
    private static void preserveEntityContext(JsonArray associatedArticles, JsonObject article) {
        JsonArray clusteredArticles = article.getJsonArray("clusteredArticles");
        clusteredArticles.forEach(clusteredArticle -> {
            copyMissingEntities(article, (JsonObject) clusteredArticle);
            associatedArticles.add(clusteredArticle);
        });
    }

    /**
     * Copies entity entries between two articles. Checks are first made to ensure that the copy does in fact need to be
     * made, in order to avoid duplicate entries.
     *
     * @param article The parent article to which any missing entities should be copied to.
     * @param clusteredArticle The article from which entities should be copied from, if the parent article does not
     *                         contain matching entries.
     */
    private static void copyMissingEntities(JsonObject article, JsonObject clusteredArticle) {
        // It is possible that neither of our articles contains an about section, so provide an empty json array if
        // required
        JsonArray clusteredArticleAbout = clusteredArticle.getJsonArray("about", new JsonArray());
        JsonArray parentArticleAbout = article.getJsonArray("about", new JsonArray());

        // Iterate the "about" entries of the parent article and check to see if they exist in the
        // clustered article, if an entry doesn't exist in clustered article, copy it across from parent
        parentArticleAbout.stream()
                .filter(aboutEntry -> !clusteredArticleAbout.contains(aboutEntry))
                .forEach(clusteredArticleAbout::add);
    }
}
