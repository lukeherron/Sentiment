package com.gofish.sentiment.newscrawler;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
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
public class NewsCrawlerWorkerTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());

        router.route("/bing/v5.0/news/search").handler(routingContext -> {
            String q = routingContext.request().params().get("q");
            HttpServerResponse response = routingContext.response();
            URL responseURL = null;

            switch (q) {
                case "error429":
                    response.setStatusCode(429);
                    responseURL = getClass().getClassLoader().getResource("data/NewsCrawler429Error.json");
                    break;
                default:
                    response.setStatusCode(200);
                    responseURL = getClass().getClassLoader().getResource("data/NewsCrawlerResponse.json");
            }

            assert responseURL != null;
            Buffer newsCrawlerResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile());
            response.end(newsCrawlerResponse);
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

        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(NewsCrawlerWorker.class.getName(), deploymentOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testNewsCrawlerReturnsExpectedJsonResultOnSuccess(TestContext context) {
        final JsonObject message = new JsonObject().put("query", "test");

        vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonObject response = (JsonObject) result.body();
            context.assertFalse(response.isEmpty());
        }));
    }

    @Test
    public void testNewsCrawlerReturnsExpectedJsonResultOnTooManyAttempts(TestContext context) {
        final JsonObject message = new JsonObject().put("query", "error429");
        final JsonObject newsCrawlerError = new JsonObject().put("error", new JsonObject()
                .put("statusCode", 429)
                .put("message", "Too many requests. Please try again in 2 seconds"));

        vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, message, context.asyncAssertFailure(cause -> {
            context.assertEquals(ReplyFailure.RECIPIENT_FAILURE, ((ReplyException) cause).failureType());
            context.assertEquals(newsCrawlerError.encode(), cause.getMessage());
        }));
    }

    @Test
    public void testNewsCrawlerFailsIfInvalidQuerySupplied(TestContext context) {
        final JsonObject invalidQueryMessage = new JsonObject().put("query", "");

        vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, invalidQueryMessage, context.asyncAssertFailure(result -> {
            context.assertEquals("Invalid Query", result.getMessage());
        }));
    }
}
