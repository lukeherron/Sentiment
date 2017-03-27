package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newsanalyser.NewsAnalyserService;
import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.article.SentimentArticle;
import com.gofish.sentiment.sentimentservice.job.AnalyserJob;
import com.gofish.sentiment.sentimentservice.job.Job;
import com.gofish.sentiment.sentimentservice.job.RetryStrategyFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisOptions;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.redis.RedisClient;
import io.vertx.rxjava.redis.RedisTransaction;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.ServiceReference;
import rx.Observable;
import rx.Single;

import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class NewsAnalyserJobMonitor extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(NewsAnalyserJobMonitor.class);
    private static final PendingQueue pendingQueue = PendingQueue.NEWS_ANALYSER;
    private static final WorkingQueue workingQueue = WorkingQueue.NEWS_ANALYSER;

    private RedisClient actionClient;
    private RedisClient monitorClient;
    private RedisOptions redisOptions = new RedisOptions().setHost("redis");
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        actionClient = RedisClient.create(vertx, redisOptions);
        monitorClient = RedisClient.create(vertx, redisOptions);
        serviceDiscovery = ServiceDiscovery.create(vertx);

        // Continually ping the redis monitor client every 5 seconds until we get a pong response
        monitorClient.rxPing()
                .toObservable()
                .retryWhen(errors -> errors.flatMap(error -> Observable.timer(5, TimeUnit.SECONDS)))
                .subscribe(pong -> {
                            monitorJobQueue(pendingQueue, workingQueue);
                            startFuture.complete();
                }, startFuture::fail);
    }

    private void monitorJobQueue(PendingQueue pendingQueue, WorkingQueue workingQueue) {
        LOG.info("Monitoring " + pendingQueue);

        monitorClient.rxBrpoplpush(pendingQueue.toString(), workingQueue.toString(), 0)
                .toObservable()
                .repeat()
                .map(JsonObject::new)
                .subscribe(this::startJob, LOG::error);
    }

    private void startJob(JsonObject jsonJob) {
        final AnalyserJob job = new AnalyserJob(jsonJob);
        final AnalyserJob original = job.copy(); // Backup original before changing state or making changes

        JsonObject article = job.getPayload();
        job.setState(Job.State.ACTIVE);

        LOG.info("Starting news analysis for job: " + job.getJobId());

        serviceDiscovery.rxGetRecord(record -> record.getName().equals(NewsAnalyserService.NAME))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<NewsAnalyserService>get)
                .flatMap(service -> Single.create(new SingleOnSubscribeAdapter<JsonObject>(handler -> service.analyseSentiment(article, handler)))
                        .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service)))
                .map(article::mergeIn)
                .subscribe(
                        result -> processCompletedJob(original, result),
                        failure -> processFailedJob(original, failure)
                );
    }

    private void processCompletedJob(AnalyserJob job, JsonObject result) {
        LOG.info("Processing of job " + job.getJobId() + " in " + workingQueue + " complete");

        actionClient.rxLrem(workingQueue.toString(), 0, job.encode())
                .subscribe(removed -> {
                    job.setResult(result);
                    job.setState(Job.State.COMPLETE);
                    announceJobResult(job);
                    LOG.info("Total number of jobs removed from " + workingQueue + " = " + removed);
                    LOG.info("Finished processing completed job in queue: " + workingQueue);
                }, LOG::error);
    }

    private void processFailedJob(AnalyserJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId(), error);

        AnalyserJob original = job.copy(); // We need to make a copy to ensure redis can find the original job in the working queue
        job.incrementAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        RedisTransaction transaction = actionClient.transaction();
        transaction.rxMulti()
                .delay(job.getTimeout(), TimeUnit.MILLISECONDS)
                .flatMap(x -> transaction.rxLrem(WorkingQueue.NEWS_ANALYSER.toString(), 0, original.encode()))
                .flatMap(x -> transaction.rxLpush(PendingQueue.NEWS_ANALYSER.toString(), job.encode()))
                .flatMap(x -> transaction.rxExec())
                .subscribe(
                        result -> LOG.info("Re-queued failed analyser job: " + result),
                        failure -> transaction.rxDiscard());

        // TODO: should this call be made in the onSuccess of the above subscribe method?
        announceJobResult(job, error);
    }

    private void announceJobResult(Job job) {
        SentimentArticle article = new SentimentArticle(job.getResult());
        vertx.eventBus().publish("news-analyser:article:" + article.getUUID(), job.toJson());
    }

    private void announceJobResult(Job job, Throwable error) {
        SentimentArticle article = new SentimentArticle(job.getResult());
        vertx.eventBus().publish("news-analyser:article:error:" + article.getUUID(), new JsonObject()
                .put("error", error.getMessage())
                .put("retryStrategy", job.getRetryStrategy()));
    }
}
