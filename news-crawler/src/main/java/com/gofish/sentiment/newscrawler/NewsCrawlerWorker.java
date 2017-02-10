package com.gofish.sentiment.newscrawler;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class NewsCrawlerWorker extends AbstractVerticle {

    static final String ADDRESS = "sentiment.crawler.worker";
    private static final int DEFAULT_API_PORT = 443;
    private static final int DEFAULT_RESULT_COUNT = 100;
    private static final int DEFAULT_CRAWL_FREQUENCY = 3600000;
    private static final int DEFAULT_WORKER_INSTANCES = 8;
    private static final String DEFAULT_FRESHNESS = "Day";
    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerWorker.class);

    private HttpClient httpClient;
    private MessageConsumer<JsonObject> messageConsumer;
    private ResponseParser responseParser = new ResponseParser();

    private String apiKey;
    private String baseUrl;
    private String freshness;
    private String urlPath;

    private Integer crawlFrequency;
    private Integer port;
    private Integer resultCount;
    private Integer workerInstances;

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
        freshness = apiConfig.getString("freshness", DEFAULT_FRESHNESS);
        urlPath = apiConfig.getString("url.path", "");

        crawlFrequency = apiConfig.getInteger("crawl.frequency", DEFAULT_CRAWL_FREQUENCY);
        port = apiConfig.getInteger("port", DEFAULT_API_PORT);
        resultCount = apiConfig.getInteger("result.count", DEFAULT_RESULT_COUNT);
        workerInstances = apiConfig.getInteger("worker.instances", DEFAULT_WORKER_INSTANCES);

        httpClient = Optional.ofNullable(httpClient).orElseGet(() -> vertx.createHttpClient(getHttpClientOptions()));

        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, messageHandler -> {
            final String query = messageHandler.body().getString("query");
            final String requestUri = String.join("", urlPath, "?q=", query);
            //final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("Ocp-Apim-Subscription-Key", apiKey);

            LOG.info("Crawling query: " + query);

//            RxHelper.get(httpClient, port, baseUrl, requestUri, headers)
//                    .doOnNext(response -> LOG.info(response.statusCode() + ": " + response.statusMessage()))
//                    .flatMap(this::bodyHandlerObservable)
//                    .map(responseParser::parse)
//                    .subscribe();
            HttpClientRequest request = httpClient.request(HttpMethod.GET, port, baseUrl, requestUri)
                    .putHeader("Ocp-Apim-Subscription-Key", apiKey);

            crawlNewsArticles(request)
                    .map(responseParser::parse)
                    .subscribe(
                            result -> messageHandler.reply(result),
                            failure -> messageHandler.fail(1, failure.getMessage()),
                            () -> request.end()
                    );
        });
    }

    private Observable<JsonObject> crawlNewsArticles(HttpClientRequest request) {
        return request.toObservable()
                .doOnNext(response -> LOG.info(response.statusCode() + ": " + response.statusMessage()))
                .flatMap(this::bodyHandlerObservable)
                .switchMap(json -> {
                    switch(("" + json.getInteger("statusCode", 0)).charAt(0)) {
                        case '4':
                        case '5':
                            return Observable.error(new Throwable(json.getString("message")));
                        default:
                            return Observable.just(json);
                    }
                })
                .retryWhen(errors -> errors.flatMap(error -> {
                    // This will keep retrying the request until the vertx reply handler times out, at which point the
                    // request should be re-queued and no longer the responsibility of this worker.
                    int delay = 2;
                    if (error.getMessage().contains("Rate limit is exceeded")) {
                        delay = Integer.parseInt(error.getMessage().replaceAll("[^\\d]", ""));
                    }

                    return Observable.timer(delay, TimeUnit.SECONDS);
                }));
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
                .setPipeliningLimit(workerInstances)
                .setIdleTimeout(0)
                .setSsl(true)
                .setKeepAlive(true);
    }
}
