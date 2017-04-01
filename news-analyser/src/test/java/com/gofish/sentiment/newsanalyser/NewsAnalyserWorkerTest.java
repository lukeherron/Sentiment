package com.gofish.sentiment.newsanalyser;

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
public class NewsAnalyserWorkerTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());

        router.route("/text/analytics/v2.0/sentiment").handler(routingContext -> {
            routingContext.request().bodyHandler(body -> {
                JsonObject request = body.toJsonObject();
                HttpServerResponse response = routingContext.response();
                URL responseURL;

                System.out.println(request.getJsonArray("documents").getJsonObject(0).getString("text"));
                switch (request.getJsonArray("documents").getJsonObject(0).getString("text").trim()) {
                    case "error429.":
                        response.setStatusCode(429);
                        responseURL = getClass().getClassLoader().getResource("data/NewsAnalyser429Error.json");
                        break;
                    default:
                        response.setStatusCode(200);
                        responseURL = getClass().getClassLoader().getResource("data/NewsAnalyserResponse.json");
                }

                assert responseURL != null;
                Buffer newsAnalyserResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile());
                response.end(newsAnalyserResponse);
            });
        });

        vertx.createHttpServer().requestHandler(router::accept).listen();

        JsonObject config = new JsonObject().put("api", new JsonObject()
                        .put("base.url", "localhost")
                        .put("url.path", "/text/analytics/v2.0/sentiment")
                        .put("port", 80)
                        .put("worker.instances", 8));

        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(NewsAnalyserWorker.class.getName(), deploymentOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testNewsAnalyserReturnsExpectedJsonResultOnSuccess(TestContext context) {
        JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("name", "test article").put("description", "test article description"));

        vertx.eventBus().send(NewsAnalyserWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonObject response = (JsonObject) result.body();
            context.assertEquals(
                    "{\"name\":\"test article\",\"description\":\"test article description\",\"sentiment\":{\"score\":0.0533933,\"id\":\"1234\"}}",
                    response.encode());
        }));
    }

    @Test
    public void testNewsAnalyserReturnsExpectedJsonResultOnTooManyAttempts(TestContext context) {
        JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("name", "error429").put("description", ""));

        vertx.eventBus().send(NewsAnalyserWorker.ADDRESS, message, context.asyncAssertFailure(cause -> {
            context.assertEquals(ReplyFailure.RECIPIENT_FAILURE, ((ReplyException) cause).failureType());
            context.assertEquals(
                    "{\"error\":{\"statusCode\":429,\"message\":\"Too many requests. Please try again in 49 seconds\"}}",
                    cause.getMessage());
        }));
    }
}
