package com.gofish.sentiment.newscrawler;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.RxHelper;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientResponse;
import rx.Observable;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class NewsCrawlerWorker extends AbstractVerticle {

    static final String ADDRESS = "sentiment.crawler.worker";
    private static final int DEFAULT_API_PORT = 443;
    private static final int DEFAULT_RESULT_COUNT = 100;
    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerWorker.class);

    private HttpClient httpClient;
    private MessageConsumer<JsonObject> messageConsumer;
    private ResponseParser responseParser = new ResponseParser();

    private String apiKey;
    private String baseUrl;
    private String urlPath;
    private Integer port;
    private Integer resultCount;

    public NewsCrawlerWorker() {
        // Vertx requires a default constructor
    }

    public NewsCrawlerWorker(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void start() throws Exception {
        LOG.info("Bringing up NewsCrawlerWorker");

        JsonObject apiConfig = Optional.ofNullable(config().getJsonObject("api"))
                .orElseThrow(() -> new RuntimeException("Could not load crawler configuration"));

        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", DEFAULT_API_PORT);
        resultCount = apiConfig.getInteger("result.count", DEFAULT_RESULT_COUNT);
        httpClient = Optional.ofNullable(httpClient).orElseGet(() -> vertx.createHttpClient(getHttpClientOptions()));

        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, messageHandler -> {
            final String query = messageHandler.body().getString("query");
            final String requestUri = String.join("", urlPath, "?q=", query);
            final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("Ocp-Apim-Subscription-Key", apiKey);

            LOG.info("Crawling query: " + query);

            RxHelper.get(httpClient, port, baseUrl, requestUri, headers)
                    .doOnNext(response -> LOG.info(response.statusCode() + ": " + response.statusMessage()))
                    .flatMap(this::bodyHandlerObservable)
                    .map(responseParser::parse)
                    .subscribe();
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        httpClient.close();
        messageConsumer.unregisterObservable().subscribe(
                stopFuture::complete,
                stopFuture::fail,
                () -> LOG.info("NewsCrawlerWorker messageConsumer unregistered")
        );
    }

    private Observable<JsonObject> bodyHandlerObservable(HttpClientResponse response) {
        ObservableFuture<JsonObject> observable = io.vertx.rx.java.RxHelper.observableFuture();

        response.bodyHandler(buffer -> {
            observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject()));
        });

        return observable;
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
}
