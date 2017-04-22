package com.gofish.sentiment.newscrawler;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
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
public class NewsCrawlerServiceTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private NewsCrawlerService newsCrawlerService;
    private JsonObject newsSearchResponse;

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());
        router.route("/bing/v5.0/news/search").handler(routingContext -> {
            String q = routingContext.request().params().get("q");
            HttpServerResponse response = routingContext.response();
            URL responseURL;

            switch (q) {
                case "error429":
                    response.setStatusCode(200); // Microsoft API sends a 200 reponse with the 429 error wrapped in the response json
                    responseURL = getClass().getClassLoader().getResource("data/NewsCrawler429Error.json");
                    break;
                default:
                    response.setStatusCode(200);
                    responseURL = getClass().getClassLoader().getResource("data/NewsCrawlerResponse.json");
            }

            assert responseURL != null;
            newsSearchResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile()).toJsonObject();
            response.end(newsSearchResponse.encode());
        });

        vertx.createHttpServer().requestHandler(router::accept).listen();

        JsonObject config = new JsonObject().put("api", new JsonObject()
                .put("base.url", "localhost")
                .put("url.path", "/bing/v5.0/news/search")
                .put("port", 80)
                .put("freshness", "Day")
                .put("result.count", 100)
                .put("worker.instances", 8)
        );

        newsCrawlerService = NewsCrawlerService.create(vertx, config);
    }

    @Test
    public void testNewsCrawlerReturnsExpectedJsonResultOnSuccess(TestContext context) {
        String query = "test";

        newsCrawlerService.crawlQuery(query, context.asyncAssertSuccess(result -> {
            context.assertFalse(result.isEmpty());
        }));
    }

    @Test
    public void testNewsCrawlerReturnsExpectedJsonResultOnTooManyAttempts(TestContext context) {
        String query = "error429";

        newsCrawlerService.crawlQuery(query, context.asyncAssertFailure(cause -> {
            context.assertEquals(newsSearchResponse.encode(), cause.getMessage());
        }));
    }

    @Test
    public void testNewsCrawlerFailsIfInvalidQuerySupplied(TestContext context) {
        String query = "";

        newsCrawlerService.crawlQuery(query, context.asyncAssertFailure(cause -> {
            context.assertEquals("Invalid Query", cause.getMessage());
        }));
    }
}
