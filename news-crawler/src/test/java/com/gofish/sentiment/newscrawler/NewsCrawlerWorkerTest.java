package com.gofish.sentiment.newscrawler;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

        newsCrawlerResponse = vertx.fileSystem().readFileBlocking("resources/data/NewsCrawlerResponse").toJsonObject();

        NewsCrawlerWorker newsCrawlerWorker = new NewsCrawlerWorker(httpClient);
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put("api", new JsonObject()));

        when(httpClient.request(any(), anyInt(), anyString(), anyString())).thenReturn(httpClientRequest);
    }
}
