package com.gofish.sentiment.newsanalyser;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.ext.web.client.HttpRequest;
import io.vertx.rxjava.ext.web.client.HttpResponse;
import io.vertx.rxjava.ext.web.client.WebClient;
import io.vertx.rxjava.ext.web.codec.BodyCodec;
import rx.Single;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Luke Herron
 */
public class NewsAnalyserWorker extends AbstractVerticle {

    public static final String ADDRESS = "sentiment.analyser.worker";
    private static final Logger LOG = LoggerFactory.getLogger(NewsAnalyserWorker.class);

    private WebClient webClient;
    private MessageConsumer<JsonObject> messageConsumer;

    private String apiKey;
    private String baseUrl;
    private String urlPath;
    private Integer port;

    public NewsAnalyserWorker() {
        // Vertx requires a default constructor
    }

    public NewsAnalyserWorker(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public void start() throws Exception {
        LOG.info("Bringing up News Analyser Worker");

        JsonObject apiConfig = Optional.ofNullable(config().getJsonObject("api"))
                .orElseThrow(() -> new RuntimeException("Could not load analyser configuration"));

        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", 443);
        webClient = Optional.ofNullable(webClient).orElseGet(() -> WebClient.create(vertx, getWebClientOptions()));

        final HttpRequest<JsonObject> request = getHttpRequest();

        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, messageHandler -> {
            final JsonObject article = messageHandler.body().getJsonObject("article");
            final String articleName = article.getString("name");
            final String articleDescription = article.getString("description");

            if (articleName == null && articleDescription == null) {
                messageHandler.fail(1, "Invalid Request");
            }
            else if (articleName == null) {
                messageHandler.fail(1, "Invalid article headline supplied");
            }
            else if (articleDescription == null) {
                messageHandler.fail(1, "Invalid article lead paragraph supplied");
            }

            final String analysisText = String.join(". ", articleName, articleDescription);
            final JsonObject requestData = new JsonObject().put("documents", new JsonArray()
                    .add(new JsonObject()
                            .put("language", "en")
                            .put("id", UUID.randomUUID().toString())
                            .put("text", analysisText)));

            request.rxSendJson(requestData)
                    .map(HttpResponse::body)
                    .flatMap(body -> rxAddSentimentResults(article, body))
                    .subscribe(messageHandler::reply, fail -> messageHandler.fail(1, fail.getMessage()));
        });
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
                .setSsl(true)
                .setKeepAlive(true);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        webClient.close();
        messageConsumer.rxUnregister().subscribe(stopFuture::complete, stopFuture::fail);
    }
}
