package com.gofish.sentiment.newscrawler;

import io.vertx.core.Future;
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

    private WebClient webClient;
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

    public NewsCrawlerWorker(WebClient webClient) {
        this.webClient = webClient;
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

        webClient = Optional.ofNullable(webClient).orElseGet(() -> WebClient.create(vertx, getWebClientOptions()));

        final HttpRequest<JsonObject> request = getHttpRequest();

        messageConsumer = vertx.eventBus().localConsumer(ADDRESS, messageHandler -> {
            final String query = messageHandler.body().getString("query");

            // Fail early on easily-discerned failures
            if (query == null || query.isEmpty()) {
                messageHandler.fail(1, "Invalid Query");
            }

            request.setQueryParam("q", query)
                    .rxSend()
                    .map(HttpResponse::body)
                    .map(responseParser::parse)
                    .subscribe(messageHandler::reply, fail -> messageHandler.fail(1, fail.getMessage()));
        });
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

    private HttpRequest<JsonObject> getHttpRequest() {
        return webClient.get(port, baseUrl, urlPath)
                .putHeader("Ocp-Apim-Subscription-Key", apiKey)
                .addQueryParam("mkt", "en-US")
                .addQueryParam("freshness", freshness)
                .addQueryParam("count", String.valueOf(resultCount))
                .as(BodyCodec.jsonObject());
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        webClient.close();
        messageConsumer.rxUnregister().subscribe(stopFuture::complete, stopFuture::fail);
    }
}
