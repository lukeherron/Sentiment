package com.gofish.sentiment.common.http;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.http.HttpClientResponse;
import rx.Observable;

import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class ResponseHandler {

    private static final int DEFAULT_RETRY_DELAY = 2;

    public static Observable<JsonObject> handle(HttpClientResponse response) {
        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
        response.bodyHandler(buffer -> observable.toHandler().handle(Future.succeededFuture(buffer.toJsonObject())));

        return observable.switchMap(json -> {
            switch(("" + json.getInteger("statusCode", 0)).charAt(0)) {
                case '4':
                case '5':
                    return Observable.error(new Throwable(json.getString("message")));
                default:
                    return Observable.just(json);
            }
        }).retryWhen(errors -> errors.flatMap(error -> {
            error.printStackTrace();
            int delay = DEFAULT_RETRY_DELAY;

            if (error.getMessage().contains("Rate limit is exceeded")) {
                delay = Integer.parseInt(error.getMessage().replaceAll("[^\\d]", ""));
            }

            System.out.println("DELAY IS: " + delay);
            return Observable.timer(delay, TimeUnit.SECONDS);
        }));
    }
}
