package com.gofish.sentiment.newslinker;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import rx.Observable;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class NewsLinkerWorker  extends AbstractVerticle {

    static final String ADDRESS = "sentiment.linker.worker";
    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerWorker.class);

    private HttpClient httpClient;
    private MessageConsumer<JsonObject> messageConsumer;

    private String apiKey;
    private String baseUrl;
    private String urlPath;
    private Integer port;

    public NewsLinkerWorker() {
        // Vertx requires a default constructor
    }

    public NewsLinkerWorker(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void start() throws Exception {
        LOG.info("Bringing up News Linker Worker");

        JsonObject apiConfig = Optional.ofNullable(config().getJsonObject("api"))
                .orElseThrow(() -> new RuntimeException("Could not load linker configuration"));

        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", 443);
        httpClient = Optional.ofNullable(httpClient).orElseGet(() -> vertx.createHttpClient(getHttpClientOptions()));

        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, messageHandler -> {
            try {
                final JsonObject article = messageHandler.body().getJsonObject("article", new JsonObject());
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
                final Buffer chunk = Buffer.buffer(text);

                HttpClientRequest request = httpClient.request(HttpMethod.POST, port, baseUrl, urlPath)
                        .putHeader("Content-Type", "text/plain; charset=UTF-8")
                        .putHeader("Content-Length", String.valueOf(chunk.length()))
                        .putHeader("Ocp-Apim-Subscription-Key", apiKey);

                LOG.info("Calling Entity Linking API");

                request.toObservable()
                        .flatMap(response -> {
                            ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                            response.bodyHandler(buffer -> observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject())));
                            return observable;
                        })
                        .flatMap(result -> this.addNewEntities(article, result))
                        .subscribe(
                                result -> messageHandler.reply(result),
                                failure -> messageHandler.fail(1, failure.getMessage()),
                                () -> {
                                    // request.end() must occur inside onComplete to avoid 'Request already complete'
                                    // exceptions which may occure if initial request fails and a retry is necessary
                                    request.end();
                                    LOG.info("Finished News Linking");
                                }
                        );

                request.write(chunk);
            }
            catch (Throwable t) {
                LOG.error(t.getMessage(), t.getCause());
                messageHandler.fail(2, "Invalid Request");
            }
        });
    }

    private Observable<JsonObject> addNewEntities(JsonObject article, JsonObject linkerResponse) {
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

        return Observable.just(article);
    }

    /**
     * Creates and returns an HttpClientOptions object. Values for each option are retrieved from the config json object
     * (this config json object is passed in to the verticle when it is deployed)
     *
     * @return HttpClientOptions object to configure this verticles HttpClient
     */
    private HttpClientOptions getHttpClientOptions() {
        return new HttpClientOptions()
                .setPipelining(true)
                .setPipeliningLimit(10)
                .setIdleTimeout(0)
                .setSsl(true)
                .setKeepAlive(true);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        httpClient.close();
        messageConsumer.rxUnregister().subscribe(stopFuture::complete, stopFuture::fail);
    }
}
