package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.job.Job;
import com.gofish.sentiment.sentimentservice.job.RetryStrategyFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.redis.RedisClient;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;

/**
 * @author Luke Herron
 */
public abstract class AbstractJobMonitor extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJobMonitor.class);

    RedisClient redis;
    ServiceDiscovery serviceDiscovery;

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

    protected abstract void startJob(JsonObject job);

    //protected abstract void setJobResult(CrawlerJob job, JsonObject jobResult);

    protected abstract void announceJobResult(Job job);

    protected abstract void announceJobResult(Job job, Throwable error);

    private void monitorJobQueue(PendingQueue pendingQueue, WorkingQueue workingQueue) {
        LOG.info("Monitoring " + pendingQueue);

        redis.brpoplpushObservable(pendingQueue.toString(), workingQueue.toString(), 0)
                .map(JsonObject::new)
                .forEach(job -> {
                    startJob(job);
                    monitorJobQueue(pendingQueue, workingQueue);
                });


//        Verticle verticle = new PopPushWorker(pendingQueue, workingQueue);
//        ((Vertx) vertx.getDelegate()).deployVerticle(verticle, new DeploymentOptions().setWorker(true), completionHandler -> {
//            if (completionHandler.succeeded()) {
//                LOG.info("DEPLOYED");
//            }
//            else {
//                LOG.error(completionHandler.cause());
//            }
//        });
//
//        vertx.eventBus().<JsonObject>localConsumer("test", handler -> {
//            startJob(handler.body());
//
//            ((Vertx) vertx.getDelegate()).deployVerticle(verticle, new DeploymentOptions().setWorker(true), completionHandler -> {
//                if (completionHandler.succeeded()) {
//                    LOG.info("DEPLOYED");
//                }
//                else {
//                    LOG.error(completionHandler.cause());
//                }
//            });
//        });
    }

    void processCompletedJob(WorkingQueue workingQueue, Job job, JsonObject jobResult) {
        LOG.info("Processing of job " + job.getJobId() + " in " + workingQueue + " complete");

        //job.setResult(jobResult);
        //announceJobResult(job);

        redis.lremObservable(workingQueue.toString(), 0, job.toJson().encode())
                .doOnNext(removed -> LOG.info("Total number of jobs removed from " + workingQueue + " = " + removed))
                .subscribe(
                        result -> {
                            job.setResult(jobResult); // TODO: can this be done by the concrete implementations?
                            //setJobResult(job, jobResult);
                            announceJobResult(job);
                        },
                        failure -> LOG.error(failure.getMessage(), failure),
                        () -> LOG.info("Finished processing completed job in queue: " + workingQueue));
    }

    void processFailedJob(Job job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId() + ": " + error.getMessage(), error);

        //Job originalJob = job.copy(); // We need to make a copy to ensure redis can find the original job in the working queue
        job.incrementAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        announceJobResult(job, error);

//        redis.lremObservable(workingQueue.toString(), 0, originalJob.toJson().encode())
//                .doOnNext(removed -> LOG.info("Total number of failed jobs removed from " + workingQueue + " = " + removed))
//                .subscribe(
//                        result -> {
//                            LOG.error(error.getMessage(), error);
//                            announceJobResult(job, error);
//                        },
//                        failure -> {
//                            LOG.error(failure.getMessage(), failure);
//                            announceJobResult(job, failure);
//                        },
//                        () -> LOG.info("Finished processing failed job in queue " + workingQueue)
//                );

        //RedisTransaction transaction = redis.transaction();
//        Observable<Long> interval = Observable.interval(job.getRetryStrategy().getInteger("retryDelay", 0), TimeUnit.SECONDS);
//        Observable<Long> queueRotation = redis.lremObservable(workingQueue.toString(), 0, originalJob.toJson().encode());
//        Observable<JsonArray> queueRotation = transaction.multiObservable()
//                        .flatMap(x -> transaction.lremObservable(workingQueue.toString(), 0, originalJob.toJson().encode()))
//                        .flatMap(x -> transaction.lpushObservable(pendingQueue.toString(), job.toJson().encode()))
//                        .flatMap(x -> transaction.execObservable());
//
//        Observable.zip(queueRotation, interval, (numRotated, timer) -> numRotated)
//                .subscribe(
//                        result -> {
//                            LOG.info("Total number of failed jobs removed from " + workingQueue + " = " + result);
//                            vertx.eventBus().send("news-linker:article:" + new SentimentArticle(job.getResult()).getUUID(), error);
//                        },
//                        failure -> LOG.error(failure.getMessage(), failure),
//                        () -> LOG.info("Finished processing failed job in queue: " + workingQueue));

//        // We want to perform 'remove from working queue' and 'push to pending queue' atomically, so use Redis Transaction
//        RedisTransaction transaction = redis.transaction();
//        transaction.multiObservable()
//                .flatMap(x -> transaction.lremObservable(workingQueue.toString(), 0, job.toJson().encode()))
//                .flatMap(x -> transaction.lpushObservable(pendingQueue.toString(), job.toJson().encode()))
//                .flatMap(x -> transaction.execObservable())
//                .doOnNext(LOG::info)
//                .subscribe(
//                        result -> LOG.info("Total number of jobs removed from " + workingQueue + " = " + result),
//                        failure -> {
//                            LOG.error(failure.getMessage(), failure);
//                            transaction.discardObservable().subscribe(LOG::error);
//                        },
//                        () -> LOG.info("Finished processing failed job in queue: " + workingQueue));
    }

    @Override
    public void stop() throws Exception {
        serviceDiscovery.close();
    }
}
