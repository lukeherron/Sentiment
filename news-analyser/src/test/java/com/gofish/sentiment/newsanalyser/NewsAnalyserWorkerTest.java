package com.gofish.sentiment.newsanalyser;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
public class NewsAnalyserWorkerTest {

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

        NewsAnalyserWorker newsAnalyserWorker = new NewsAnalyserWorker(httpClient);
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put("api", new JsonObject()));

        vertx.deployVerticle(newsAnalyserWorker, deploymentOptions, context.asyncAssertSuccess());

        when(httpClient.request(any(), anyInt(), anyString(), anyString())).thenReturn(httpClientRequest);
        when(httpClientRequest.putHeader(anyString(), anyString())).thenReturn(httpClientRequest);
        when(httpClientRequest.toObservable()).thenReturn(Observable.just(httpClientResponse));
    }

    @Test
    public void testNewsAnalyserReturnsExpectedJsonResultOnSuccess(TestContext context) {
        JsonObject message = new JsonObject().put("article", new JsonObject()
                .put("name", "test article").put("description", "test article description"));

        JsonObject mockResponse = new JsonObject().put("documents", new JsonArray().add(new JsonObject().put("result", 0.83)));

        mockBodyHandler(mockResponse);

        vertx.eventBus().send(NewsAnalyserWorker.ADDRESS, message, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonObject response = (JsonObject) result.body();
            context.assertEquals(
                    message.copy().getJsonObject("article").put("sentiment", new JsonObject().put("result", 0.83)), response);
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
