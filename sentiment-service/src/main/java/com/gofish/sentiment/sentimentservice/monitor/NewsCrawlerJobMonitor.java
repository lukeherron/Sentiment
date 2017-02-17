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
import io.vertx.rxjava.redis.RedisTransaction;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;

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
        redis.brpoplpushObservable(SentimentService.NEWS_CRAWLER_PENDING_QUEUE, SentimentService.NEWS_CRAWLER_WORKING_QUEUE, 0)
                .repeat()
                .map(JsonObject::new)
                .map(SentimentJob::new)
                .forEach(this::startNewsSearchJob);
    }

    private void startNewsSearchJob(SentimentJob job) {
        LOG.info("Starting news search for job: " + job.getJobId());

        // When pushing the job to our linker and analyser queues, those queues must have access to the news search result
        // in order to complete their portion of the work. This requires that the news search response is provided to the
        // job before it reaches those queues, but when we modify the job we can't use it to remove the job from the
        // news crawler working queue, as it will no longer match. For this reason, we create a copy before we modify it
        // so that we can successfully remove it.
        SentimentJob originalJob = new SentimentJob(job.toJson());
        RedisTransaction transaction = redis.transaction();

        EventBusService.<NewsCrawlerService>getProxyObservable(serviceDiscovery, NewsCrawlerService.class.getName())
                .flatMap(service -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.crawlQuery(job.getQuery(), observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return observable;
                })
                .doOnNext(job::setNewsSearchResponse)
                // Ensure pushing to two queues and removing from one happens atomically by utilising RedisTransaction
                .flatMap(result -> transaction.multiObservable()
                        .flatMap(x -> transaction.lpushObservable(SentimentService.NEWS_LINKER_PENDING_QUEUE, job.toJson().encode()))
                        .flatMap(x -> transaction.lpushObservable(SentimentService.NEWS_ANALYSER_PENDING_QUEUE, job.toJson().encode()))
                        .flatMap(x -> transaction.lremObservable(SentimentService.NEWS_CRAWLER_WORKING_QUEUE, 0, originalJob.toJson().encode()))
                ) // TODO: handle push errors, implement retry etc.
                .subscribe(
                        result -> {
                            LOG.info("Pushing jobs to entity linking and sentiment pending queues");
                            transaction.execObservable().subscribe(LOG::info);
                        },
                        failure -> {
                            LOG.error(failure);
                            transaction.discardObservable().subscribe(LOG::error);
                        },
                        () -> LOG.info("Completed news search crawl for job: " + job.getJobId())
                );
    }
}
