package com.gofish.sentiment.newsanalyser;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Luke Herron
 */
public class NewsAnalyserServiceImpl implements NewsAnalyserService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsAnalyserServiceImpl.class);

    private final Vertx vertx;
    private final WebClient webClient;
    private final HttpRequest<JsonObject> request;
    private final CircuitBreaker breaker;
    private final String apiKey;
    private final String baseUrl;
    private final String urlPath;
    private final Integer port;

    private final AtomicLong timeoutTimeStamp;
    private final AtomicLong timeoutDelay;

    public NewsAnalyserServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        JsonObject apiConfig = config.getJsonObject("api");
        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", 443);

        webClient = WebClient.create(vertx, getWebClientOptions());
        request = getHttpRequest();
        breaker = CircuitBreaker.create("newsanalyser-circuit-breaker", vertx,
                new CircuitBreakerOptions().setMaxRetries(5).setMaxFailures(5).setTimeout(30000).setResetTimeout(30000));

        timeoutTimeStamp = new AtomicLong(0);
        timeoutDelay = new AtomicLong(0);
    }

    @Override
    public void analyseSentiment(JsonObject article, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Starting sentiment analysis");

        final String articleName = article.getString("name");
        final String articleDescription = article.getString("description");

        if (articleName == null && articleDescription == null) {
            resultHandler.handle(Future.failedFuture("Invalid Request"));
        }
        else if (articleName == null) {
            resultHandler.handle(Future.failedFuture("Invalid article headline supplied"));
        }
        else if (articleDescription == null) {
            resultHandler.handle(Future.failedFuture("Invalid article lead paragraph supplied"));
        }
        else {
            final String text = String.join(". ", articleName, articleDescription);
            final JsonObject requestData = new JsonObject().put("documents", new JsonArray()
                    .add(new JsonObject()
                            .put("language", "en")
                            .put("id", UUID.randomUUID().toString())
                            .put("text", text)));

            rxAnalyseSentiment(requestData)
                    .map(HttpResponse::body)
                    .flatMap(body -> rxAddSentimentResults(article, body))
                    .subscribe(RxHelper.toSubscriber(resultHandler));
        }
    }

    /**
     * Takes request data and sends it to the Microsoft Cognitive Services API. This API processes the text submitted in
     * the request and returns a sentiment analysis score as part of the response
     * @param requestData JsonObject representing the request data to send
     * @return Single which emits the result of the HttpResponse
     */
    private Single<HttpResponse<JsonObject>> rxAnalyseSentiment(JsonObject requestData) {

        return Single.create(new SingleOnSubscribeAdapter<HttpResponse<JsonObject>>(fut -> {
            breaker.<HttpResponse<JsonObject>>execute(future -> {
                request.sendJson(requestData, response -> {
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
     * Updates a supplied news article with the supplied sentiment analysis response
     * @param article JsonObject which represents the news article to be updated with the sentiment results
     * @param analysisResponse JsonObject which holds the sentiment analysis results
     * @return Single which emits the news article which has been updated with the sentiment results
     */
    private Single<JsonObject> rxAddSentimentResults(JsonObject article, JsonObject analysisResponse) {
        JsonArray documents = Optional.ofNullable(analysisResponse.getJsonArray("documents"))
                .orElseThrow(() -> new RuntimeException(analysisResponse.containsKey("error") ?
                        analysisResponse.encode() : new JsonObject().put("error", analysisResponse).encode()));

        article.put("sentiment", documents.getJsonObject(0));

        return Single.just(article);
    }

    /**
     * Retrieves the HttpRequest, configured for access to the Microsoft Cognitive Services API
     * @return HTTP client request object
     */
    private HttpRequest<JsonObject> getHttpRequest() {

        return webClient.post(port, baseUrl, urlPath)
                .putHeader("Ocp-Apim-Subscription-Key", apiKey)
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
