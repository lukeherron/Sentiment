package com.gofish.sentiment.newslinker;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class NewsLinkerServiceTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private NewsLinkerService newsLinkerService;
    private JsonObject entityLinkingResponse;

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();

        Router router = Router.router(vertx);
        router.route("/entitylinking/v1.0/link").handler(routingContext -> {
            routingContext.request().bodyHandler(body -> {
                String request = body.toString().trim();
                HttpServerResponse response = routingContext.response();
                URL responseURL;

                switch (request) {
                    case "error429.":
                        response.setStatusCode(200);
                        responseURL = getClass().getClassLoader().getResource("data/NewsLinker429Error.json");
                        break;
                    default:
                        response.setStatusCode(200);
                        responseURL = getClass().getClassLoader().getResource("data/EntityLinkerResponse.json");
                }

                assert responseURL != null;
                entityLinkingResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile()).toJsonObject();
                response.end(entityLinkingResponse.encode());
            });
        });

        vertx.createHttpServer().requestHandler(router::accept).listen();

        JsonObject config = new JsonObject().put("api", new JsonObject()
                .put("base.url", "localhost")
                .put("url.path", "/entitylinking/v1.0/link")
                .put("port", 80)
                .put("worker.instances", 8));

        newsLinkerService = NewsLinkerService.create(vertx, config);
    }

    @Test
    public void testNewsLinkerReturnsExpectedJsonResultOnSuccess(TestContext context) {
        final JsonObject article = new JsonObject()
                .put("name", "Apple’s hot new W1-equipped BeatsX wireless earbuds have finally been released.")
                .put("description", "Last night, there were only three pairs of headphones on the planet equipped with Apple’s W1 wireless chip. The most talked-about W1 headphones are obviously Apple’s truly wireless earbuds, the AirPods, which are still next to impossible to find unless ...")
                .put("about", new JsonArray().add(new JsonObject().put("name", "Apple Inc.")));

        newsLinkerService.linkEntities(article, context.asyncAssertSuccess(result -> {
            URL responseURL = getClass().getClassLoader().getResource("data/NewsLinkerResponse.json");;
            assert responseURL != null;
            JsonObject newsLinkingResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile()).toJsonObject();

            context.assertEquals(newsLinkingResponse.encodePrettily(), result.encodePrettily());
        }));
    }

    @Test
    public void testNewsLinkerReturnsExpectedJsonResultOnFailure(TestContext context) {
        final JsonObject article = new JsonObject()
                .put("name", "error429")
                .put("description", "")
                .put("about", new JsonArray().add(new JsonObject().put("name", "entity1 test")));

        newsLinkerService.linkEntities(article, context.asyncAssertFailure(cause -> {
            context.assertEquals(new JsonObject().put("error", entityLinkingResponse).encode(), cause.getMessage());
        }));
    }

    @Test
    public void testNewsLinkerFailsIfInvalidArticleSupplied(TestContext context) {
        final JsonObject invalidArticle = new JsonObject().put("invalid", "");

        newsLinkerService.linkEntities(invalidArticle, context.asyncAssertFailure(cause -> {
            context.assertEquals("Invalid Request", cause.getMessage());
        }));
    }
}
