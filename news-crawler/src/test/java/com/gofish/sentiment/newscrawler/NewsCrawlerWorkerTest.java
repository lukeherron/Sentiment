package com.gofish.sentiment.newscrawler;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
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

import java.net.URL;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class NewsCrawlerWorkerTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private HttpClient httpClient;
    private HttpClientRequest httpClientRequest;
    private HttpClientResponse httpClientResponse;
    private JsonObject newsCrawlerResponse;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();
        httpClient = mock(HttpClient.class);
        httpClientRequest = mock(HttpClientRequest.class);
        httpClientResponse = mock(HttpClientResponse.class);

        URL responseURL = getClass().getClassLoader().getResource("data/NewsCrawlerResponse.json");
        assert responseURL != null;
        newsCrawlerResponse = vertx.fileSystem().readFileBlocking(responseURL.getFile()).toJsonObject();

        NewsCrawlerWorker newsCrawlerWorker = new NewsCrawlerWorker(httpClient);
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put("api", new JsonObject()));

        vertx.deployVerticle(newsCrawlerWorker, deploymentOptions, context.asyncAssertSuccess());

        when(httpClient.request(any(), anyInt(), anyString(), anyString())).thenReturn(httpClientRequest);
        when(httpClientRequest.putHeader(anyString(), anyString())).thenReturn(httpClientRequest);
        when(httpClientRequest.toObservable()).thenReturn(Observable.just(httpClientResponse));
    }

    @Test
    public void testNewsCrawlerReturnsExpectedJsonResultOnSuccess(TestContext context) {
        final JsonObject message = new JsonObject().put("query", "test");
        mockBodyHandler(newsCrawlerResponse);

        vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonObject response = (JsonObject) result.body();
            context.assertFalse(response.isEmpty());
        }));
    }

    @Test
    public void testNewsCrawlerReturnsExpectedJsonResultOnTooManyAttempts(TestContext context) {
        final JsonObject message = new JsonObject().put("query", "test");
        final JsonObject newsCrawlerError = new JsonObject().put("error", new JsonObject()
                .put("statusCode", 429)
                .put("message", "Too many requests. Please try again in 2 seconds"));

        mockBodyHandler(newsCrawlerError);

        vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, message, context.asyncAssertFailure(cause -> {
            context.assertEquals(ReplyFailure.RECIPIENT_FAILURE, ((ReplyException) cause).failureType());
            context.assertEquals(newsCrawlerError.encode(), cause.getMessage());
        }));
    }

    @Test
    public void testNewsCrawlerFailsIfInvalidQuerySupplied(TestContext context) {
        final JsonObject invalidQueryMessage = new JsonObject().put("query", "");

        mockBodyHandler(newsCrawlerResponse);

        vertx.eventBus().send(NewsCrawlerWorker.ADDRESS, invalidQueryMessage, context.asyncAssertFailure(result -> {
            context.assertEquals("Invalid Query", result.getMessage());
        }));
    }

    private void mockBodyHandler(JsonObject newsCrawlerResponse) {
        when(httpClientResponse.bodyHandler(any())).thenAnswer(invocation -> {
            Handler<Buffer> handler = invocation.getArgument(0);
            handler.handle(Buffer.buffer(newsCrawlerResponse.encode()));
            return invocation.getMock();
        });
    }
}
