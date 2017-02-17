package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newsanalyser.NewsAnalyserService;
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
import io.vertx.rxjava.core.eventbus.MessageConsumer;
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

    private RedisClient redis;
    private ServiceDiscovery serviceDiscovery;
    private MessageConsumer<JsonObject> analyseConsumer;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up News Analyser job monitor");

        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);

        redis.ping(resultHandler -> {
            if (resultHandler.succeeded()) {
                monitorNewsAnalyserJobQueue();
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private void monitorNewsAnalyserJobQueue() {
        redis.brpoplpushObservable(SentimentService.NEWS_ANALYSER_PENDING_QUEUE, SentimentService.NEWS_ANALYSER_WORKING_QUEUE, 0)
                .repeat()
                .map(JsonObject::new)
                .map(SentimentJob::new)
                .forEach(this::startNewsAnalysisJob);
    }

    private void startNewsAnalysisJob(SentimentJob job) {
        LOG.info("Starting news analysis for job: " + job.getJobId());

        EventBusService.<NewsAnalyserService>getProxyObservable(serviceDiscovery, NewsAnalyserService.class.getName())
                .flatMap(service -> doRateLimitedAnalyserRequest(job.getNewsSearchResponse(), service))
                //.doOnNext(job::setSentimentResponse)
                .subscribe(
                        result -> processCompletedJob(job, result),
                        failure -> processFailedJob(job, failure),
                        () -> LOG.info("Completed news analysis job")
                );
    }

    private Observable<JsonObject> doRateLimitedAnalyserRequest(JsonObject newsSearchResponse, NewsAnalyserService service) {
        // Avoid modifying the original job until we are certain the request is going to be successful (i.e. the job has
        // been removed from any active queues first). To avoid this, we make a copy of newsSearchResponse and make
        // changes to the copy. This is necessary because newsSearchResponse is embedded in the original job, and any
        // changes to the JsonObject are reflected in the job
        JsonObject workingCopy = newsSearchResponse.copy();

        Observable<Object> articles = Observable.from(workingCopy.getJsonArray("value"));
        Observable<Long> interval = Observable.interval(400, TimeUnit.MILLISECONDS);

        return Observable.zip(articles, interval, (observable, timer) -> observable)
                .map(json -> (JsonObject) json)
                .flatMap(json -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.analyseSentiment(json, observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return observable.map(json::mergeIn);
                })
                .lastOrDefault(workingCopy);
    }

    private void processCompletedJob(SentimentJob job, JsonObject jobResult) {
        redis.lremObservable(SentimentService.NEWS_ANALYSER_WORKING_QUEUE, 0, job.toJson().encode())
                .doOnNext(removed -> LOG.info("Total number of jobs removed from " + SentimentService.NEWS_ANALYSER_WORKING_QUEUE + ": " + removed))
                .subscribe(
                        result -> {
                            job.setSentimentResponse(jobResult);
                            vertx.eventBus().send("news-analyser:" + job.getJobId(), job.toJson());
                        },
                        failure -> LOG.error(failure.getMessage(), failure),
                        () -> LOG.info("Finished processing completed job in queue: " + SentimentService.NEWS_ANALYSER_WORKING_QUEUE));
    }

    private void processFailedJob(SentimentJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId() + ": " + error.getMessage(), error);

        // TODO: inspect error and decide on the retry strategy
        // We want to perform remove from working queue and push to pending queue atomically, so use Redis Transaction
        RedisTransaction transaction = redis.transaction();
        transaction.multiObservable()
                .flatMap(x -> transaction.lremObservable(SentimentService.NEWS_ANALYSER_WORKING_QUEUE, 0, job.toJson().encode()))
                .flatMap(x -> transaction.lpushObservable(SentimentService.NEWS_ANALYSER_PENDING_QUEUE, job.toJson().encode()))
                .subscribe(
                        result -> transaction.execObservable().subscribe(info -> LOG.info(info.encodePrettily())),
                        failure -> {
                            LOG.error(failure.getMessage(), failure);
                            transaction.discardObservable().subscribe(LOG::error);
                        },
                        () -> LOG.info("Finished processing failed job in queue: " + SentimentService.NEWS_ANALYSER_WORKING_QUEUE));
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }
}
