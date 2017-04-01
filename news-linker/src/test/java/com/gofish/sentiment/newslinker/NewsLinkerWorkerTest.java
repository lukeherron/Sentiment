package com.gofish.sentiment.newslinker;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
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
public class NewsLinkerWorkerTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());

        router.route("/entitylinking/v1.0/link").handler(routingContext -> {
            routingContext.request().bodyHandler(body -> {
                String request = body.toString().trim();
                HttpServerResponse response = routingContext.response();
                URL responseURL;

                switch (request) {
                    case "error429.":
                        response.setStatusCode(429);
                        responseURL = getClass().getClassLoader().getResource("data/NewsLinker429Error.json");
                        break;
                    default:
                        response.setStatusCode(200);
                        responseURL = getClass().getClassLoader().getResource("data/NewsLinkerResponse.json");
                }

                System.out.println("REQUEST: " + request);
                assert responseURL != null;
                Buffer newsLinkerResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile());
                response.end(newsLinkerResponse);
            });
        });

        vertx.createHttpServer().requestHandler(router::accept).listen();

        JsonObject config = new JsonObject().put("api", new JsonObject()
                .put("base.url", "localhost")
                .put("url.path", "/entitylinking/v1.0/link")
                .put("port", 80)
                .put("worker.instances", 8));

        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(NewsLinkerWorker.class.getName(), deploymentOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testNewsLinkerReturnsExpectedJsonResultOnSuccess(TestContext context) {
        final JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("name", "test article")
                .put("description", "test description")
                .put("about", new JsonArray().add(new JsonObject().put("name", "entity1 test"))));

        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            // We should receive a response which is a combination of 'message' and 'entityLinkResponse' in a single
            // JsonObject (a 'readLink' entry is also added in prod, and will be seen in this test)
            JsonObject response = (JsonObject) result.body();
            context.assertNotNull(response);
            context.assertEquals(
                    "{\"name\":\"test article\",\"description\":\"test description\",\"about\":[{\"name\":\"entity1 test\"},{\"name\":\"Apple Inc.\",\"readLink\":\"\"},{\"name\":\"iPad\",\"readLink\":\"\"},{\"name\":\"Chief Executive Officer\",\"readLink\":\"\"},{\"name\":\"Tim Cook\",\"readLink\":\"\"},{\"name\":\"iPhone\",\"readLink\":\"\"}]}",
                    response.encode());
        }));
    }

    @Test
    public void testNewsLinkerReturnsExpectedJsonResultOnFailure(TestContext context) {
        final JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("name", "error429")
                .put("description", "")
                .put("about", new JsonArray().add(new JsonObject().put("name", "entity1 test"))));

        final JsonObject newsLinkerError = new JsonObject().put("error", new JsonObject()
                .put("statusCode", 429)
                .put("message", "Too many requests. Please try again in 2 seconds"));

        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, message, context.asyncAssertFailure(cause -> {
            context.assertEquals(ReplyFailure.RECIPIENT_FAILURE, ((ReplyException) cause).failureType());
            context.assertEquals(newsLinkerError.encode(), cause.getMessage());
        }));
    }

    @Test
    public void testNewsLinkerFailsIfInvalidArticleSupplied(TestContext context) {
        final JsonObject invalidArticleMessage = new JsonObject().put("invalid", "");

        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, invalidArticleMessage, context.asyncAssertFailure(result -> {
            context.assertEquals("Invalid Request", result.getMessage());
        }));
    }
}
