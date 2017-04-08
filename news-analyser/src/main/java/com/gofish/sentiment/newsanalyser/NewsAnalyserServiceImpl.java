package com.gofish.sentiment.newsanalyser;

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

/**
 * @author Luke Herron
 */
public class NewsAnalyserServiceImpl implements NewsAnalyserService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsAnalyserServiceImpl.class);

    private final WebClient webClient;
    private final HttpRequest<JsonObject> request;
    private final String apiKey;
    private final String baseUrl;
    private final String urlPath;
    private final Integer port;

    public NewsAnalyserServiceImpl(Vertx vertx, JsonObject config) {
        JsonObject apiConfig = config.getJsonObject("api");
        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", 443);

        webClient = WebClient.create(vertx, getWebClientOptions());
        request = getHttpRequest();
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

    private Single<HttpResponse<JsonObject>> rxAnalyseSentiment(JsonObject requestData) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut ->
                request.sendJson(requestData, fut)));
    }

    private Single<JsonObject> rxAddSentimentResults(JsonObject article, JsonObject analysisResponse) {
        JsonArray documents = Optional.ofNullable(analysisResponse.getJsonArray("documents"))
                .orElseThrow(() -> new RuntimeException(analysisResponse.encode()));

        article.put("sentiment", documents.getJsonObject(0));

        return Single.just(article);
    }

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
}
