package com.gofish.sentiment.newscrawler;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import rx.Single;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Luke Herron
 */
public class NewsCrawlerServiceImpl implements NewsCrawlerService {

    private static final int DEFAULT_API_PORT = 443;
    private static final int DEFAULT_RESULT_COUNT = 100;
    private static final String DEFAULT_FRESHNESS = "Day";
    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerServiceImpl.class);

    private final WebClient webClient;
    private final HttpRequest<JsonObject> request;
    private final CircuitBreaker breaker;
    private final String apiKey;
    private final String baseUrl;
    private final String freshness;
    private final String urlPath;
    private final Integer port;
    private final Integer resultCount;

    private final AtomicLong timeoutTimeStamp;
    private final AtomicLong timeoutDelay;

    public NewsCrawlerServiceImpl(Vertx vertx, JsonObject config) {
        JsonObject apiConfig = config.getJsonObject("api");
        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        freshness = apiConfig.getString("freshness", DEFAULT_FRESHNESS);
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", DEFAULT_API_PORT);
        resultCount = apiConfig.getInteger("result.count", DEFAULT_RESULT_COUNT);

        webClient = WebClient.create(vertx, getWebClientOptions());
        request = getHttpRequest();
        breaker = CircuitBreaker.create("newscrawler-circuit-breaker", vertx,
                new CircuitBreakerOptions().setMaxRetries(5).setMaxFailures(5).setTimeout(30000).setResetTimeout(30000));

        timeoutTimeStamp = new AtomicLong(0);
        timeoutDelay = new AtomicLong(0);
    }

    @Override
    public void crawlQuery(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        // Fail early on easily-discerned failures
        if (query == null || query.isEmpty()) {
            resultHandler.handle(Future.failedFuture("Invalid Query"));
        }

        LOG.info("Starting crawl for query: " + query);

        rxCrawlQuery(query)
                .map(HttpResponse::body)
                .map(ResponseParser::parse)
                .subscribe(RxHelper.toSubscriber(resultHandler));
    }

    /**
     * Takes the query string and sends it to the Bing News Search API. This API performs a news search, returning
     * articles which are related to the supplied query.
     * @param query String query to search the news for
     * @return Single which emits the result of the HttpResponse
     */
    private Single<HttpResponse<JsonObject>> rxCrawlQuery(String query) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut -> {
            breaker.<HttpResponse<JsonObject>>execute(future -> {
                request.setQueryParam("q", query).send(response -> {
                    if (response.failed()) {
                        LOG.error(response.cause().getMessage(), response.cause());
                        future.fail(response.cause());
                    }

                    HttpResponse<JsonObject> result = response.result();
                    if (result.statusCode() != 200 && result.statusCode() != 429) {
                        future.fail(result.body() == null ? result.statusMessage() : result.body().encode());
                    } else {
                        future.complete(result);
                    }
                });
            }).setHandler(fut);
        }));
    }

    /**
     * Retrieves the HttpRequest, configured for access to the Microsoft Cognitive Services API
     * @return HTTP client request object
     */
    private HttpRequest<JsonObject> getHttpRequest() {

        return webClient.get(port, baseUrl, urlPath)
                .putHeader("Ocp-Apim-Subscription-Key", apiKey)
                .addQueryParam("mkt", "en-US")
                .addQueryParam("freshness", freshness)
                .addQueryParam("count", String.valueOf(resultCount))
                .as(BodyCodec.jsonObject());
    }

    /**
     * Creates and returns an HttpClientOptions object. Values for each option are retrieved from the config json object
     * (this config json object is passed in to the verticle when it is deployed)
     *
     * @return HttpClientOptions object to configure this verticles HttpClient
     */
    private WebClientOptions getWebClientOptions() {

        return new WebClientOptions()
                .setPipelining(true)
                .setPipeliningLimit(10)
                .setIdleTimeout(0)
                .setSsl(!baseUrl.equalsIgnoreCase("localhost"))
                .setKeepAlive(true);
    }

    @Override
    public void getTimeout(Handler<AsyncResult<Long>> timeoutHandler) {
        final long delay = timeoutDelay.get() - (System.currentTimeMillis() - timeoutTimeStamp.get());
        timeoutHandler.handle(Future.succeededFuture(delay));
    }

    @Override
    public void setTimeout(Long delay) {
        timeoutTimeStamp.set(System.currentTimeMillis());
        timeoutDelay.set(delay);
    }
}
