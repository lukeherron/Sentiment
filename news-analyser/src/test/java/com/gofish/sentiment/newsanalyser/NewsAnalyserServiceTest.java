package com.gofish.sentiment.newsanalyser;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class NewsAnalyserServiceTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private NewsAnalyserService newsAnalyserService;
    private JsonObject sentimentAnalysisResponse;

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());
        router.route("/text/analytics/v2.0/sentiment").handler(routingContext -> {
            routingContext.request().bodyHandler(body -> {
                JsonObject request = body.toJsonObject();
                HttpServerResponse response = routingContext.response();
                URL responseURL;

                switch (request.getJsonArray("documents").getJsonObject(0).getString("text").trim()) {
                    case "error429.":
                        response.setStatusCode(200);
                        responseURL = getClass().getClassLoader().getResource("data/NewsAnalyser429Error.json");
                        break;
                    default:
                        response.setStatusCode(200);
                        responseURL = getClass().getClassLoader().getResource("data/NewsAnalyserResponse.json");
                }

                assert responseURL != null;
                sentimentAnalysisResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile()).toJsonObject();
                response.end(sentimentAnalysisResponse.encode());
            });
        });

        vertx.createHttpServer().requestHandler(router::accept).listen();

        JsonObject config = new JsonObject().put("api", new JsonObject()
                        .put("base.url", "localhost")
                        .put("url.path", "/text/analytics/v2.0/sentiment")
                        .put("port", 80)
                        .put("worker.instances", 8));

        newsAnalyserService = NewsAnalyserService.create(vertx, config);
    }

    @Test
    public void testNewsAnalyserReturnsExpectedJsonResultOnSuccess(TestContext context) {
        JsonObject article = new JsonObject()
                .put("name", "test article")
                .put("description", "test article description");

        newsAnalyserService.analyseSentiment(article, context.asyncAssertSuccess(result -> {
            JsonArray documents = sentimentAnalysisResponse.getJsonArray("documents");
            article.put("sentiment", documents.getJsonObject(0));

            context.assertEquals(article.encodePrettily(), result.encodePrettily());
        }));
    }

    @Test
    public void testNewsAnalyserReturnsExpectedJsonResultOnTooManyAttempts(TestContext context) {
        JsonObject article = new JsonObject().put("name", "error429").put("description", "");

        newsAnalyserService.analyseSentiment(article, context.asyncAssertFailure(cause -> {
            context.assertEquals(sentimentAnalysisResponse.encode(), cause.getMessage());
        }));
    }

    @Test
    public void testNewsAnalyserFailsIfInvalidArticleSupplied(TestContext context) {
        final JsonObject invalidArticle = new JsonObject().put("invalid", "");

        newsAnalyserService.analyseSentiment(invalidArticle, context.asyncAssertFailure(cause -> {
            context.assertEquals("Invalid Request", cause.getMessage());
        }));
    }
}
