package com.gofish.sentiment.newslinker;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.ext.web.client.HttpRequest;
import io.vertx.rxjava.ext.web.client.HttpResponse;
import io.vertx.rxjava.ext.web.client.WebClient;
import io.vertx.rxjava.ext.web.codec.BodyCodec;
import rx.Observable;
import rx.Single;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class NewsLinkerWorker  extends AbstractVerticle {

    static final String ADDRESS = "sentiment.linker.worker";
    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerWorker.class);

    private WebClient webClient;
    private MessageConsumer<JsonObject> messageConsumer;

    private String apiKey;
    private String baseUrl;
    private String urlPath;
    private Integer port;

    public NewsLinkerWorker() {
        // Vertx requires a default constructor
    }

    public NewsLinkerWorker(WebClient webClient) { this.webClient = webClient; }

    @Override
    public void start() throws Exception {
        LOG.info("Bringing up News Linker Worker");

        JsonObject apiConfig = Optional.ofNullable(config().getJsonObject("api"))
                .orElseThrow(() -> new RuntimeException("Could not load linker configuration"));

        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", 443);
        webClient = Optional.ofNullable(webClient).orElseGet(() -> WebClient.create(vertx, getWebClientOptions()));

        final HttpRequest<JsonObject> request = getHttpRequest();

        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, messageHandler -> {
            JsonObject article = messageHandler.body().getJsonObject("article", new JsonObject());
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

            final String text = String.join(". ", articleName, articleDescription);
            request.rxSendStream(Observable.just(Buffer.buffer(text)))
                    .map(HttpResponse::body)
                    .flatMap(body -> rxAddNewEntities(article, body))
                    .subscribe(messageHandler::reply, fail -> messageHandler.fail(1, fail.getMessage()));
        });
    }

    private Single<JsonObject> rxAddNewEntities(JsonObject article, JsonObject linkerResponse) {
        JsonArray responseEntities = Optional.ofNullable(linkerResponse.getJsonArray("entities"))
                .orElseThrow(() -> new RuntimeException(linkerResponse.encode()));

        JsonArray articleEntities = article.getJsonArray("about", new JsonArray());

        // Add any entities to articleEntities if they can only be found in responseEntities
        responseEntities.stream()
                .map(entity -> (JsonObject) entity)
                .filter(entity -> articleEntities.stream()
                        .map(articleEntity -> ((JsonObject) articleEntity).getString("name"))
                        .noneMatch(name -> entity.getString("name").equalsIgnoreCase(name)))
                .map(entity -> new JsonObject()
                        .put("name", entity.getString("name"))
                        .put("readLink", ""))
                .forEach(articleEntities::add);

        return Single.just(article);
    }

    private HttpRequest<JsonObject> getHttpRequest() {
        return webClient.post(port, baseUrl, urlPath)
                .putHeader("Content-Type", "text/plain; charset=UTF-8")
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
