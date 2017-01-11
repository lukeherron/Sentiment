package com.gofish.sentiment.verticle;

import com.gofish.sentiment.rxjava.service.CrawlerService;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.RxHelper;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.LoggerHandler;
import rx.Observable;

/**
 * @author Luke Herron
 */
public class APIGatewayVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(APIGatewayVerticle.class);

    private CrawlerService crawlerService;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config());
        RxHelper.deployVerticle(vertx, new MongoVerticle())
                .flatMap(id -> RxHelper.deployVerticle(vertx, new CrawlerVerticle(), deploymentOptions))
                .subscribe(
                        result -> startServer(startFuture),
                        failure -> startFuture.fail(failure),
                        () -> logger.info("Required supporting verticles deployed")
                );
    }

    private void startServer(Future<Void> startFuture) {
        crawlerService = CrawlerService.createProxy(vertx, CrawlerVerticle.ADDRESS);

        Router router = Router.router(vertx);

        router.route().handler(LoggerHandler.create());

        router.route("/search").blockingHandler(requestHandler -> {
            String q = requestHandler.request().params().get("q");
            if (q != null && !q.isEmpty()) {
                final String query = q.toLowerCase();
                HttpServerResponse response = requestHandler.response();
                getSentiment(query).subscribe(
                        response::end,
                        requestHandler::fail,
                        () -> logger.info("Finished retrieving sentiment"));
            }
            else {
                requestHandler.fail(400); // Bad Request
            }
        });

        // Setup a failure handler
        router.route("/*").failureHandler(failureHandler -> {
            int statusCode = failureHandler.statusCode();
            HttpServerResponse response = failureHandler.response();
            response.setStatusCode(statusCode).end("Sorry! Not today");
        });

        // Launch server and start listening
        vertx.createHttpServer().requestHandler(router::accept).listenObservable().subscribe(
                result -> startFuture.complete(),
                failure -> startFuture.fail(failure),
                () -> logger.info("HttpServer started")
        );
    }

    private Observable<String> getSentiment(String query) {
        return crawlerService.isQueryActiveObservable(query)
                .flatMap(isActive -> {
                    logger.info("Is query active?: " + isActive);
                    return isActive ?
                            Observable.just(new JsonObject().put("response", "query active").encodePrettily()) :
                            crawlerService.addNewQueryObservable(query)
                                    .flatMap(result -> Observable.just(new JsonObject().put("response", "query not active").encodePrettily()));
                });
    }
}
