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
import io.vertx.rxjava.redis.RedisClient;
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
        redis.brpoplpushObservable(SentimentService.SENTIMENT_PENDING, SentimentService.SENTIMENT_WORKING, 0)
                .repeat()
                .map(JsonObject::new)
                .map(SentimentJob::new)
                .forEach(this::startNewsAnalysisJob);
    }

    private void startNewsAnalysisJob(SentimentJob job) {
        LOG.info("Starting news analysis for job: " + job.getJobId());

        EventBusService.<NewsAnalyserService>getProxyObservable(serviceDiscovery, NewsAnalyserService.class.getName())
                .flatMap(service -> doRateLimitedAnalyserRequest(job.getNewsSearchResponse(), service))
                .subscribe(
                        result -> vertx.eventBus().send("news-analyser:" + job.getJobId(), job.getNewsSearchResponse()),
                        failure -> vertx.eventBus().send("news-analyser:" + job.getJobId(), new JsonObject().put("error", failure.getMessage())),
                        () -> LOG.info("Completed sentiment analysis")
                );
    }

    private Observable<JsonObject> doRateLimitedAnalyserRequest(JsonObject newsSearchResponse, NewsAnalyserService service) {
        Observable<Object> articles = Observable.from(newsSearchResponse.getJsonArray("value"));
        Observable<Long> interval = Observable.interval(400, TimeUnit.MILLISECONDS);

        return Observable.zip(articles, interval, (observable, timer) -> observable)
                .map(json -> (JsonObject) json)
                .flatMap(json -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.analyseSentiment(json, observable.toHandler());
                    return observable.map(json::mergeIn);
                })
                .lastOrDefault(newsSearchResponse);
    }
}
