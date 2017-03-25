package com.gofish.sentiment.newscrawler;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
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

import java.util.Optional;

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

            // Fail early on easily-discerned failures
            if (query == null || query.isEmpty()) {
                messageHandler.fail(1, "Invalid Query");
            }

            final String requestUri = String.join("", urlPath,
                    "?q=", query,
                    "&mkt=", "en-US",
                    "&freshness=", freshness,
                    "&count=", String.valueOf(resultCount));

            final Buffer chunk = Buffer.buffer();
            //final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("Ocp-Apim-Subscription-Key", apiKey);

            LOG.info("Crawling query: " + query);

            HttpClientRequest request = httpClient.request(HttpMethod.GET, port, baseUrl, requestUri)
                    .putHeader("Content-Length", String.valueOf(chunk.length()))
                    .putHeader("Ocp-Apim-Subscription-Key", apiKey);

            request.toObservable()
                    .flatMap(response -> {
                        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                        response.bodyHandler(buffer -> observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject())));
                        return observable;
                    })
                    .map(responseParser::parse)
                    .subscribe(
                            result -> messageHandler.reply(result),
                            failure -> messageHandler.fail(1, failure.getMessage()),
                            () -> {
                                // request.end() must occur inside onComplete to avoid 'Request already complete'
                                // exceptions which may occure if initial request fails and a retry is necessary
                                request.end();
                                LOG.info("Finished crawling request: " + query);
                            }
                    );

            request.write(chunk);
        });
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
