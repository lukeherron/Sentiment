package com.gofish.sentiment.api;

import com.gofish.sentiment.sentimentservice.SentimentService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.LoggerHandler;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.ServiceReference;
import rx.Single;

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

                rxGetSentiment(query).subscribe(
                        result -> {
                            response.end(String.valueOf(result));
                            LOG.info("Finished retrieving sentiment");
                        },
                        failure -> {
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

        // Launch server and start listening
        vertx.createHttpServer().requestHandler(router::accept).rxListen().subscribe(
                result -> startFuture.complete(),
                failure -> startFuture.fail(failure));
    }

    private Single<JsonObject> rxGetSentiment(String query) {
        return serviceDiscovery.rxGetRecord(record -> record.getName().equals(SentimentService.NAME))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<SentimentService>get)
                .flatMap(service -> Single.create(new SingleOnSubscribeAdapter<JsonObject>(handler -> service.getSentiment(query, handler)))
                    .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service)));
    }

    @Override
    public void stop() throws Exception {
        serviceDiscovery.close();
    }
}
