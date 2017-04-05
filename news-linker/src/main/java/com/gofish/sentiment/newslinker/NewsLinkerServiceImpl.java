package com.gofish.sentiment.newslinker;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import rx.Observable;
import rx.Single;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class NewsLinkerServiceImpl implements NewsLinkerService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerServiceImpl.class);

    private final WebClient webClient;
    private final HttpRequest<JsonObject> request;
    private final String apiKey;
    private final String baseUrl;
    private final String urlPath;
    private final Integer port;

    public NewsLinkerServiceImpl(Vertx vertx, JsonObject config) {
        JsonObject apiConfig = config.getJsonObject("api");
        apiKey = apiConfig.getString("key", "");
        baseUrl = apiConfig.getString("base.url", "");
        urlPath = apiConfig.getString("url.path", "");
        port = apiConfig.getInteger("port", 443);

        webClient = WebClient.create(vertx, getWebClientOptions());
        request = getHttpRequest();
    }

    @Override
    public void linkEntities(JsonObject article, Handler<AsyncResult<JsonObject>> resultHandler) {
        LOG.info("Starting entity linking");

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
        final Buffer buffer = Buffer.buffer(text);
        final ReadStream<Buffer> readStream = RxHelper.toReadStream(Observable.just(buffer));

        rxLinkEntities(readStream)
                .map(HttpResponse::body)
                .flatMap(body -> rxAddNewEntities(article, body))
                .subscribe(RxHelper.toSubscriber(resultHandler));
    }

    private Single<HttpResponse<JsonObject>> rxLinkEntities(ReadStream<Buffer> readStream) {

        return Single.create(new SingleOnSubscribeAdapter<HttpResponse<JsonObject>>(fut ->
            request.sendStream(readStream, fut)));
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
                .setSsl(!baseUrl.equalsIgnoreCase("localhost"))
                .setKeepAlive(true);
    }
}
