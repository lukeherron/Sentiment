package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newslinker.NewsLinkerService;
import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.article.SentimentArticle;
import com.gofish.sentiment.sentimentservice.job.Job;
import com.gofish.sentiment.sentimentservice.job.LinkerJob;
import com.gofish.sentiment.sentimentservice.job.RetryStrategyFactory;
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
import rx.Observable;

import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class NewsLinkerJobMonitor extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerJobMonitor.class);
    private static final PendingQueue pendingQueue = PendingQueue.NEWS_LINKER;
    private static final WorkingQueue workingQueue = WorkingQueue.NEWS_LINKER;

    private RedisClient actionClient;
    private RedisClient monitorClient;
    private RedisOptions redisOptions = new RedisOptions().setHost("redis");
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        actionClient = RedisClient.create(vertx, redisOptions);
        monitorClient = RedisClient.create(vertx, redisOptions);
        serviceDiscovery = ServiceDiscovery.create(vertx);

        monitorClient.ping(resultHandler -> {
            if (resultHandler.succeeded()) {
                monitorJobQueue(pendingQueue, workingQueue);
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private void monitorJobQueue(PendingQueue pendingQueue, WorkingQueue workingQueue) {
        LOG.info("Monitoring " + pendingQueue);

        transferJobObservable(pendingQueue, workingQueue)
                .repeat()
                .map(JsonObject::new)
                .subscribe(this::startJob, LOG::error, ()-> LOG.info("News Linker job transfer complete"));
    }

    private Observable<String> transferJobObservable(PendingQueue pendingQueue, WorkingQueue workingQueue) {
        // When calling redis clients' brpoplpushObservable it appears unable to chain a repeat() call as onComplete is
        // never called (as per rxJava docs, repeat occurs once onComplete happens). For this reason, I've taken the
        // route of wrapping this call in our own observable, and calling onComplete immediately after onNext
        return Observable.create(subscriber -> {
            monitorClient.brpoplpushObservable(pendingQueue.toString(), workingQueue.toString(), 0)
                    .subscribe(onNext -> {
                        subscriber.onNext(onNext);
                        subscriber.onCompleted();
                    }, subscriber::onError);
        });
    }

    private void startJob(JsonObject jsonJob) {
        final LinkerJob job = new LinkerJob(jsonJob);
        final LinkerJob original = job.copy(); // Backup original before changing state or making changes
        //job.setState(Job.State.ACTIVE);

        LOG.info("Starting news linking for job: " + job.getJobId());

        EventBusService.<NewsLinkerService>getProxyObservable(serviceDiscovery, NewsLinkerService.class.getName())
                .flatMap(service -> {
                    Observable<JsonObject> entities = getLinkEntitiesObservable(service, job.getPayload());
                    job.setState(Job.State.ACTIVE);
                    return entities;
                })
                .subscribe(
                        result -> processCompletedJob(original, result),
                        failure -> processFailedJob(original, failure),
                        () -> LOG.info("Completed news linking job"));
    }

    private Observable<JsonObject> getLinkEntitiesObservable(NewsLinkerService service, JsonObject json) {
        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
        service.linkEntities(json, observable.toHandler());
        ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
        return observable.map(json::mergeIn);
    }

    private void processCompletedJob(LinkerJob job, JsonObject result) {
        LOG.info("Processing of job " + job.getJobId() + " in " + workingQueue + " complete");

        actionClient.lremObservable(workingQueue.toString(), 0, job.encode())
                .doOnNext(removed -> LOG.info("Total number of jobs removed from " + workingQueue + " = " + removed))
                .subscribe(
                        removeResult -> {
                            job.setResult(result);
                            job.setState(Job.State.COMPLETE);
                            announceJobResult(job);
                        },
                        failure -> LOG.error(failure.getMessage(), failure),
                        () -> LOG.info("Finished processing completed job in queue: " + workingQueue));
    }

    private void processFailedJob(LinkerJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId(), error);

        LinkerJob original = job.copy(); // We need to make a copy to ensure redis can find the original job in the working queue
        job.incrementAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        RedisTransaction transaction = actionClient.transaction();
        transaction.multiObservable()
                .delay(job.getTimeout(), TimeUnit.MILLISECONDS)
                .flatMap(x -> transaction.lremObservable(WorkingQueue.NEWS_LINKER.toString(), 0, original.encode()))
                .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_LINKER.toString(), job.encode()))
                .flatMap(x -> transaction.execObservable())
                .subscribe(
                        result -> LOG.info("Re-queued failed linker job: " + result),
                        failure -> transaction.discardObservable(),
                        () -> LOG.info("Re-queue complete"));

        announceJobResult(job, error);
    }

    private void announceJobResult(Job job) {
        SentimentArticle article = new SentimentArticle(job.getResult());
        vertx.eventBus().publish("news-linker:article:" + article.getUUID(), job.toJson());
    }

    private void announceJobResult(Job job, Throwable error) {
        SentimentArticle article = new SentimentArticle(job.getResult());
        vertx.eventBus().publish("news-linker:article:error:" + article.getUUID(), new JsonObject()
                .put("error", error.getMessage())
                .put("retryStrategy", job.getRetryStrategy()));
    }
}
