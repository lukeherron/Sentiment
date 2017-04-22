package com.gofish.sentiment.api;

import com.gofish.sentiment.sentimentservice.rxjava.SentimentService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.LoggerHandler;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.Record;
import rx.Observable;
import rx.Single;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Luke Herron
 */
public class APIGatewayVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(APIGatewayVerticle.class);

    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());

        router.route("/search").blockingHandler(requestHandler -> {
            String q = requestHandler.request().params().get("q");
            if (q != null && !q.isEmpty()) {
                final String query = q.toLowerCase();
                HttpServerResponse response = requestHandler.response();

                rxGetService(SentimentService.name(), SentimentService.class)
                        .flatMap(service -> service.rxGetSentiment(query))
                        .subscribe(result -> {
                            LOG.info("Finished retrieving sentiment");
                            response.end(result.encode());
                        }, failure -> {
                            LOG.error(failure.getMessage(), failure);
                            requestHandler.fail(failure);
                        });
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

        JsonArray serviceDependencies = new JsonArray().add(SentimentService.name());
        serviceDiscovery = ServiceDiscovery.create(vertx, serviceDiscovery -> {
            LOG.info("Service Discovery initialised");

            serviceDiscovery.rxGetRecords((JsonObject) null)
                    .doOnSuccess(r -> LOG.info("Searching for service dependencies"))
                    .map(records -> records.stream().map(Record::getName).collect(Collectors.toList()).containsAll(serviceDependencies.getList()))
                    .flatMap(dependenciesMet -> dependenciesMet ? Single.<Void>just(null) : Single.error(new Throwable()))
                    .retryWhen(errors -> errors.flatMap(error -> Observable.timer(5, TimeUnit.SECONDS)))
                    .flatMap(v -> vertx.createHttpServer().requestHandler(router::accept).rxListen())
                    .subscribe(r -> startFuture.complete(), startFuture::fail);
        });
    }

    private <T> Single<T> rxGetService(String recordName, Class<T> clazz) {

        return Single.create(new SingleOnSubscribeAdapter<T>(fut ->
                EventBusService.getServiceProxy(serviceDiscovery, record -> record.getName().equals(recordName), clazz, fut)
        ));
    }

    @Override
    public void stop() throws Exception {
        serviceDiscovery.close();
    }
}
