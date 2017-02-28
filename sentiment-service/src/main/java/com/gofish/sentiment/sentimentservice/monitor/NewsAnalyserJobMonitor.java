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
                .subscribe(this::startJob, LOG::error, () -> LOG.info("News Analyser job transfer complete"));
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
        AnalyserJob job = new AnalyserJob(jsonJob);
        job.setState(Job.State.ACTIVE);

        LOG.info("Starting news analysis for job: " + job.getJobId());

        EventBusService.<NewsAnalyserService>getProxyObservable(serviceDiscovery, NewsAnalyserService.class.getName())
                .flatMap(service -> getAnalyseSentimentObservable(service, job.getArticle().copy()))
                .subscribe(
                        result -> processCompletedJob(workingQueue, job, result),
                        failure -> processFailedJob(job, failure),
                        () -> LOG.info("Completed news analysis job"));
    }

    private Observable<JsonObject> getAnalyseSentimentObservable(NewsAnalyserService service, JsonObject article) {
        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
        service.analyseSentiment(article, observable.toHandler());
        ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
        return observable.map(article::mergeIn);
    }

    private void processCompletedJob(WorkingQueue workingQueue, AnalyserJob job, JsonObject jobResult) {
        LOG.info("Processing of job " + job.getJobId() + " in " + workingQueue + " complete");

        actionClient.lremObservable(workingQueue.toString(), 0, job.toJson().encode())
                .doOnNext(removed -> LOG.info("Total number of jobs removed from " + workingQueue + " = " + removed))
                .subscribe(
                        result -> {
                            job.setResult(jobResult);
                            job.setState(Job.State.COMPLETE);
                            announceJobResult(job);
                        },
                        failure -> LOG.error(failure.getMessage(), failure),
                        () -> LOG.info("Finished processing completed job in queue: " + workingQueue));
    }

    private void processFailedJob(AnalyserJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId(), error);

        Job originalJob = job.copy(); // We need to make a copy to ensure redis can find the original job in the working queue
        job.incrementAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        RedisTransaction transaction = actionClient.transaction();

        transaction.multiObservable()
                .delay(job.getTimeout(), TimeUnit.MILLISECONDS)
                .flatMap(x -> transaction.lremObservable(WorkingQueue.NEWS_ANALYSER.toString(), 0, originalJob.encode()))
                .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_ANALYSER.toString(), job.encode()))
                .flatMap(x -> transaction.execObservable())
                .subscribe(
                        result -> LOG.info(result.encodePrettily()),
                        failure -> transaction.discardObservable(),
                        () -> LOG.info("Re-queued news analyser job"));

        announceJobResult(job, error);
    }

    private void announceJobResult(Job job) {
        SentimentArticle article = new SentimentArticle(job.getResult());
        vertx.eventBus().publish("news-analyser:article:" + article.getUUID(), job.toJson());
    }

    private void announceJobResult(Job job, Throwable error) {
        SentimentArticle article = new SentimentArticle(job.getResult());
        vertx.eventBus().publish("news-analyser:error:article:" + article.getUUID(), new JsonObject()
                .put("error", error.getMessage())
                .put("retryStrategy", job.getRetryStrategy()));
    }
}
