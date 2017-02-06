package com.gofish.sentiment.newslinker;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import rx.Observable;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class NewsLinkerWorkerTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private HttpClient httpClient;
    private HttpClientRequest httpClientRequest;
    private HttpClientResponse httpClientResponse;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();
        httpClient = mock(HttpClient.class);
        httpClientRequest = mock(HttpClientRequest.class);
        httpClientResponse = mock(HttpClientResponse.class);

        NewsLinkerWorker newsLinkerWorker = new NewsLinkerWorker(httpClient);
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put("api", new JsonObject()));

        vertx.deployVerticle(newsLinkerWorker, deploymentOptions, context.asyncAssertSuccess());

        when(httpClient.request(any(), anyInt(), anyString(), anyString())).thenReturn(httpClientRequest);
        when(httpClientRequest.putHeader(anyString(), anyString())).thenReturn(httpClientRequest);
        when(httpClientRequest.toObservable()).thenReturn(Observable.just(httpClientResponse));
    }

    @Test
    public void testNewsLinkerReturnsExpectedJsonResultOnSuccess(TestContext context) {
        final JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("about", new JsonArray().add(new JsonObject().put("name", "entity1 test"))));

        final JsonObject entityLinkResponse = new JsonObject()
                .put("entities", new JsonArray().add(new JsonObject().put("name", "entity2 test")));

        final JsonObject expectedTestResult = new JsonObject()
                .put("about", new JsonArray()
                        .add(new JsonObject().put("name", "entity1 test"))
                        .add(new JsonObject().put("name", "entity2 test").put("readLink", "")));

        mockBodyHandler(entityLinkResponse);

        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            // We should receive a response which is a combination of 'message' and 'entityLinkResponse' in a single
            // JsonObject (a 'readLink' entry is also added in prod, and will be seen in this test)
            JsonObject response = (JsonObject) result.body();
            context.assertNotNull(response);
            context.assertEquals( expectedTestResult.encode(), response.encode());
        }));
    }

    @Test
    public void testNewsLinkerReturnsExpectedJsonResultOnFailure(TestContext context) {
        final JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("about", new JsonArray().add(new JsonObject().put("name", "entity1 test"))));

        final JsonObject entityLinkResponse = new JsonObject()
                .put("entities", new JsonArray().add(new JsonObject().put("name", "entity2 test")))
                .put("statusCode", 429)
                .put("message", "Rate limit is exceeded: 5");

        mockBodyHandler(entityLinkResponse);

        // Change the reply timeout before sending the message. If we fail to do this, then the default timeout will
        // be observed (usually 30 seconds), slowing down the unit test considerably
        DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(500);
        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, message, deliveryOptions, context.asyncAssertFailure(result -> {
            context.assertEquals("Timed out after waiting " + deliveryOptions.getSendTimeout() + "(ms) for a reply. address: 1", result.getMessage());
        }));
    }

    @Test
    public void testNewsLinkerFailsIfInvalidArticleSupplied(TestContext context) {
        final JsonObject invalidArticleMessage = new JsonObject().put("invalid", "");

        final JsonObject entityLinkResponse = new JsonObject()
                .put("entities", new JsonArray().add(new JsonObject().put("name", "entity2 test")))
                .put("statusCode", 200)
                .put("message", "Success");

        mockBodyHandler(entityLinkResponse);

        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, invalidArticleMessage, context.asyncAssertFailure(result -> {
            context.assertEquals("Invalid Request", result.getMessage());
        }));
    }

    @Test
    public void testNewsLinkerSucceedsWithNoLinkingIfInvalidResponseReceived(TestContext context) {
        final JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("about", new JsonArray().add(new JsonObject().put("name", "entity1 test"))));

        final JsonObject invalidEntityLinkResponse = new JsonObject()
                .put("invalid", new JsonArray().add(new JsonObject().put("alsoInvalid", "entity2 test")));

        mockBodyHandler(invalidEntityLinkResponse);

        vertx.eventBus().send(NewsLinkerWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            // As the response did not contain any info that could be linked, we should be receiving back the original
            // article contained inside the message, with no changes
            context.assertEquals(message.getJsonObject("article"), result.body());
        }));
    }

    private void mockBodyHandler(JsonObject entityLinkResponse) {
        when(httpClientResponse.bodyHandler(any())).thenAnswer(invocation -> {
            Handler<Buffer> handler = invocation.getArgument(0);
            handler.handle(Buffer.buffer(entityLinkResponse.encode()));
            return invocation.getMock();
        });
    }
}
