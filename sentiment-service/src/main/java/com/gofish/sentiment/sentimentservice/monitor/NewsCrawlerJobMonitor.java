package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newscrawler.NewsCrawlerService;
import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.job.SentimentJob;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.redis.RedisTransaction;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;

/**
 * @author Luke Herron
 */
public class NewsCrawlerJobMonitor extends AbstractJobMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerJobMonitor.class);
    private static final PendingQueue pendingQueue = PendingQueue.NEWS_CRAWLER;
    private static final WorkingQueue workingQueue = WorkingQueue.NEWS_CRAWLER;

    @Override
    protected PendingQueue getPendingQueue() {
        return pendingQueue;
    }

    @Override
    protected WorkingQueue getWorkingQueue() {
        return workingQueue;
    }

    @Override
    protected void startJob(SentimentJob job) {
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
                        .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_LINKER.toString(), job.toJson().encode()))
                        .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_ANALYSER.toString(), job.toJson().encode()))
                        .flatMap(x -> transaction.lremObservable(workingQueue.toString(), 0, originalJob.toJson().encode()))
                )
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

    @Override
    protected void setJobResult(SentimentJob job, JsonObject jobResult) {
        job.setNewsSearchResponse(jobResult);
    }

    @Override
    protected void announceJobResult(SentimentJob job) {
        // No-op
    }
}
