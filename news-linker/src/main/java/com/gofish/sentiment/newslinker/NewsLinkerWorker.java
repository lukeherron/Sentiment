package com.gofish.sentiment.newslinker;

import com.gofish.sentiment.common.http.ResponseHandler;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
                final JsonObject article = messageHandler.body().getJsonObject("article");
                final String text = String.join(". ", article.getString("name"), article.getString("description"));
                final Buffer chunk = Buffer.buffer(text);

                HttpClientRequest request = httpClient.request(HttpMethod.POST, port, baseUrl, urlPath)
                        .putHeader("Content-Type", "text/plain; charset=UTF-8")
                        .putHeader("Content-Length", String.valueOf(chunk.length()))
                        .putHeader("Ocp-Apim-Subscription-Key", apiKey);

                LOG.info("Calling Entity Linking API");

                request.toObservable()
                        .flatMap(ResponseHandler::handle)
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
                t.printStackTrace();
                messageHandler.fail(2, "Invalid Request");
            }
        });
    }

    private Observable<JsonObject> addNewEntities(JsonObject article, JsonObject linkerResponse) {
        JsonArray articleEntities = article.getJsonArray("about", new JsonArray());
        JsonArray responseEntities = linkerResponse.getJsonArray("entities", new JsonArray());

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
                .setPipeliningLimit(8)
                .setIdleTimeout(0)
                .setSsl(true)
                .setKeepAlive(true);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        messageConsumer.unregisterObservable().subscribe(
                stopFuture::complete,
                stopFuture::fail,
                () -> LOG.info("NewsLinkerWorker messageConsumer unregistered")
        );
    }
}
