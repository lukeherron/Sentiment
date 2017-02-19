package com.gofish.sentiment.api;

import com.gofish.sentiment.sentimentservice.SentimentService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.LoggerHandler;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import rx.Observable;

/**
 * @author Luke Herron
 */
public class APIGatewayVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(APIGatewayVerticle.class);

    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        serviceDiscovery = ServiceDiscovery.create(vertx);

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());

        router.route("/search").blockingHandler(requestHandler -> {
            String q = requestHandler.request().params().get("q");
            if (q != null && !q.isEmpty()) {
                final String query = q.toLowerCase();
                HttpServerResponse response = requestHandler.response();

                getSentiment(query).subscribe(
                        result -> response.end(String.valueOf(result)),
                        failure -> {
                            LOG.error(failure.getMessage(), failure);
                            requestHandler.fail(failure);
                        },
                        () -> LOG.info("Finished retrieving sentiment"));
            }
            else {
                requestHandler.fail(400); // Bad request
            }
        });

        // Setup a failure handler
        router.route("/*").failureHandler(failureHandler -> {
            HttpServerResponse response = failureHandler.response();

            LOG.error(failureHandler.failure());

            int statusCode = failureHandler.statusCode();
            if (statusCode < 0) {
                response.end("Unknown failure occurred");
            }
            else {
                response.setStatusCode(statusCode).end("Sorry! Not today");
            }
        });

        // Launch server and start listening
        vertx.createHttpServer().requestHandler(router::accept).listenObservable().subscribe(
                result -> startFuture.complete(),
                failure -> startFuture.fail(failure),
                () -> LOG.info("HttpServer started")
        );
    }

    private Observable<JsonObject> getSentiment(String query) {
        return EventBusService.<SentimentService>getProxyObservable(serviceDiscovery, SentimentService.class.getName())
                .flatMap(service -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.getSentiment(query, observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return observable;
                });
    }

    @Override
    public void stop() throws Exception {
        serviceDiscovery.close();
    }
}
