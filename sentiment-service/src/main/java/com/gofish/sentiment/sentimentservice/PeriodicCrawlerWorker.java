package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisOptions;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.redis.RedisClient;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.ServiceReference;
import rx.Observable;
import rx.Single;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        LOG.info("Bringing up PeriodicCrawlerVerticle");

        config = Optional.ofNullable(config()).orElseGet(JsonObject::new);
        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);

        // Continually ping the redis monitor client every 5 seconds until we get a pong response
        redis.rxPing()
                .toObservable()
                .retryWhen(errors -> errors.flatMap(error -> Observable.timer(5, TimeUnit.SECONDS)))
                .subscribe(pong -> {
                    startPeriodicCrawl();
                    startFuture.complete();
                }, startFuture::fail);
    }

    private void startPeriodicCrawl() {
        LOG.info("Starting periodic crawl");

        vertx.periodicStream(config.getInteger("timer.delay", DEFAULT_TIMER_DELAY))
                .toObservable()
                .flatMapSingle(id -> rxGetStorageService())
                .retryWhen(this::rxGetRetryStrategy)
                .flatMapSingle(service -> Single.create(new SingleOnSubscribeAdapter<>(service::getCollections))
                        .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service)))
                .flatMap(Observable::from)
                .map(query -> (String) query)
                .flatMapSingle(this::rxStartAnalysis)
                .subscribe(
                        result -> LOG.info("Queued crawl request for query: " + result),
                        failure -> LOG.error(failure),
                        () -> LOG.info("Periodic crawl is complete")
                );
    }

    private Single<StorageService> rxGetStorageService() {
        return serviceDiscovery.rxGetRecord(record -> record.getName().equals(StorageService.NAME))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<StorageService>get);
    }

    private Observable<Long> rxGetRetryStrategy(Observable<? extends Throwable> attempts) {
        return attempts.zipWith(Observable.range(1, 100), (n, i) -> i)
                .flatMap(i -> Observable.timer(i, TimeUnit.SECONDS));
    }

    private Single<String> rxStartAnalysis(String query) {
        return serviceDiscovery.rxGetRecord(record -> record.getName().equals(SentimentService.NAME))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<SentimentService>get)
                // We aren't concerned with returning results from the analysis, as we won't be using them
                // i.e. we only want to queue the jobs and let them run on their own. We provide a handler
                // out of necessity but we do not wait on it and simply return the original query
                .map(service -> service.analyseSentiment(query, resultHandler -> {}))
                .map(service -> {
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return query;
                });
    }
}
