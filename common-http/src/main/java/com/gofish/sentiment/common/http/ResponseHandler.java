package com.gofish.sentiment.common.http;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.http.HttpClientResponse;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class ResponseHandler {

    private static final int DEFAULT_RETRY_DELAY = 2;
    private static final Logger LOG = LoggerFactory.getLogger(ResponseHandler.class.getName());

    public static Observable<JsonObject> handle(HttpClientResponse response) {
        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
        response.bodyHandler(buffer -> observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject())));

        return observable.switchMap(json -> {
            JsonObject error = json.getJsonObject("error", new JsonObject());
            Integer statusCode = Optional.ofNullable(error.getInteger("statusCode"))
                    .orElseGet(() -> json.getInteger("statusCode"));

            // status code can still be null, in which case we assign 0 which will invoke the default case of our switch
            // statement, i.e. there is no error status, so simply return the response as is.
            statusCode = Optional.ofNullable(statusCode).orElse(0);

            switch(("" + statusCode).charAt(0)) {
                case '4':
                case '5':
                    String message = Optional.ofNullable(error.getString("message"))
                            .orElseGet(() -> json.getString("message"));
                    Throwable throwable = new Throwable(String.join(": ", String.valueOf(statusCode), message));
                    return Observable.error(throwable);
                default:
                    return Observable.just(json);
            }
        }).retryWhen(errors -> errors.flatMap(error -> {
            // TODO: remove retrying from the response handler and allow SentimentJob to handle this responsibility
            LOG.error(error.getMessage());
            int delay = DEFAULT_RETRY_DELAY;

            if (error.getMessage().contains("Rate limit is exceeded")) {
                delay = Integer.parseInt(error.getMessage().replaceAll("[^\\d]", ""));
            }

            return Observable.timer(delay, TimeUnit.SECONDS);
        }));
    }
}
