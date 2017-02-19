package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.job.RetryStrategyFactory;
import com.gofish.sentiment.sentimentservice.job.CrawlerJob;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.redis.RedisClient;
import io.vertx.rxjava.redis.RedisTransaction;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;

/**
 * @author Luke Herron
 */
public abstract class AbstractJobMonitor extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJobMonitor.class);

    protected RedisClient redis;
    protected ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);

        redis.ping(resultHandler -> {
            if (resultHandler.succeeded()) {
                monitorJobQueue(getPendingQueue(), getWorkingQueue());
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    protected abstract PendingQueue getPendingQueue();

    protected abstract WorkingQueue getWorkingQueue();

    protected abstract void startJob(CrawlerJob job);

    protected abstract void setJobResult(CrawlerJob job, JsonObject jobResult);

    protected abstract void announceJobResult(CrawlerJob job);

    private void monitorJobQueue(PendingQueue pendingQueue, WorkingQueue workingQueue) {
        redis.brpoplpushObservable(pendingQueue.toString(), workingQueue.toString(), 0)
                .repeat()
                .map(JsonObject::new)
                .map(CrawlerJob::new)
                .forEach(this::startJob);
    }

    void processCompletedJob(WorkingQueue workingQueue, PendingQueue pendingQueue, CrawlerJob job, JsonObject jobResult) {
        redis.lremObservable(workingQueue.toString(), 0, job.toJson().encode())
                .doOnNext(removed -> LOG.info("Total number of jobs removed from " + workingQueue + " = " + removed))
                .subscribe(
                        result -> {
                            setJobResult(job, jobResult);
                            announceJobResult(job);
                        },
                        failure -> processFailedJob(workingQueue, pendingQueue, job, failure),
                        () -> LOG.info("Finished processing completed job in queue: " + workingQueue));
    }

    void processFailedJob(WorkingQueue workingQueue, PendingQueue pendingQueue, CrawlerJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId() + ": " + error.getMessage(), error);

        job.updateFailedAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        // We want to perform 'remove from working queue' and 'push to pending queue' atomically, so use Redis Transaction
        RedisTransaction transaction = redis.transaction();
        transaction.multiObservable()
                .flatMap(x -> transaction.lremObservable(workingQueue.toString(), 0, job.toJson().encode()))
                .flatMap(x -> transaction.lpushObservable(pendingQueue.toString(), job.toJson().encode()))
                .subscribe(
                        result -> transaction.execObservable().subscribe(info -> LOG.info(info.encodePrettily())),
                        failure -> {
                            LOG.error(failure.getMessage(), failure);
                            transaction.discardObservable().subscribe(LOG::error);
                        },
                        () -> LOG.info("Finished processing failed job in queue: " + workingQueue));
    }

    @Override
    public void stop() throws Exception {
        serviceDiscovery.close();
    }
}
