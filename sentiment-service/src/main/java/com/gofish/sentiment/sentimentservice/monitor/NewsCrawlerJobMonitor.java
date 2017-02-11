package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newscrawler.NewsCrawlerService;
import com.gofish.sentiment.sentimentservice.SentimentJob;
import com.gofish.sentiment.sentimentservice.SentimentService;
import io.vertx.core.Future;
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

/**
 * @author Luke Herron
 */
public class NewsCrawlerJobMonitor extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerJobMonitor.class);

    private RedisClient redis;
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up news crawler job monitor");

        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);

        redis.ping(resultHandler -> {
            if (resultHandler.succeeded()) {
                monitorNewsCrawlerJobQueue();
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private void monitorNewsCrawlerJobQueue() {
        redis.brpoplpushObservable(SentimentService.NEWSCRAWLER_PENDING, SentimentService.NEWSCRAWLER_WORKING, 0)
                .repeat()
                .map(JsonObject::new)
                .map(SentimentJob::new)
                .forEach(this::startNewsSearchJob);
    }

    private void startNewsSearchJob(SentimentJob job) {
        LOG.info("Starting news search for job: " + job.getJobId());

        EventBusService.<NewsCrawlerService>getProxyObservable(serviceDiscovery, NewsCrawlerService.class.getName())
                .flatMap(service -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.crawlQuery(job.getQuery(), observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return observable;
                })
                .doOnNext(job::addResult)
                .map(result -> Observable.merge(
                        redis.lpushObservable(SentimentService.ENTITYLINK_PENDING, job.toJson().encode()),
                        redis.lpushObservable(SentimentService.SENTIMENT_PENDING, job.toJson().encode())
                )) // TODO: handle push errors, implement retry etc.
                .subscribe(
                        result -> LOG.info("Pushing jobs to entity linking and sentiment pending queues"),
                        failure -> LOG.error(failure),
                        () -> LOG.info("Completed news search crawl for job: " + job.getJobId())
                );
    }
}
