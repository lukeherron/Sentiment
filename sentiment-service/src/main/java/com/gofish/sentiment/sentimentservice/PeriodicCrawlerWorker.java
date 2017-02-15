package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisOptions;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.redis.RedisClient;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import rx.Observable;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class PeriodicCrawlerWorker extends AbstractVerticle {

    private static final int DEFAULT_TIMER_DELAY = 3600000;
    private static final Logger LOG = LoggerFactory.getLogger(PeriodicCrawlerWorker.class);

    private JsonObject config;
    private RedisClient redis;
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up NewsLinkerVerticle");

        config = Optional.ofNullable(config()).orElseGet(JsonObject::new);
        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);

        // We need to be certain that the job queues are up and running before we generate any jobs
        redis.ping(resultHandler -> {
            if (resultHandler.succeeded()) {
                startPeriodicCrawl();
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private void startPeriodicCrawl() {
        vertx.periodicStream(config.getInteger("timer.delay", DEFAULT_TIMER_DELAY))
                .toObservable()
                .flatMap(id -> EventBusService.<StorageService>getProxyObservable(serviceDiscovery, StorageService.class.getName()))
                .flatMap(storageService -> {
                    ObservableFuture<JsonArray> observable = RxHelper.observableFuture();
                    storageService.getCollections(observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, storageService);
                    return observable;
                })
                .flatMap(Observable::from)
                .map(query -> (String) query)
                .flatMap(query -> EventBusService.<SentimentService>getProxyObservable(serviceDiscovery, SentimentService.class.getName())
                        .flatMap(sentimentService -> {
                            // We aren't concerned with returning results from the analysis, as we won't be using them
                            // i.e. we only want to queue the jobs and let them run on their own. We provide a handler
                            // out of necessity but we do not wait on it and simply return the original query
                            sentimentService.analyseSentiment(query, resultHandler -> {});
                            return Observable.just(query);
                        }))
                .subscribe(
                        result -> LOG.info("Queued crawl request for query: " + result),
                        failure -> LOG.error(failure),
                        () -> LOG.info("Periodic crawl is complete")
                );
    }
}
