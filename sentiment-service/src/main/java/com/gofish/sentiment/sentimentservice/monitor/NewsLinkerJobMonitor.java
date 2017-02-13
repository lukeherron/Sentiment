package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newslinker.NewsLinkerService;
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
public class NewsLinkerJobMonitor extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerJobMonitor.class);

    private RedisClient redis;
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up News Linker job monitor");

        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);

        redis.ping(resultHandler -> {
            if (resultHandler.succeeded()) {
                monitorNewsLinkerJobQueue();
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private void monitorNewsLinkerJobQueue() {
        redis.brpoplpushObservable(SentimentService.NEWS_LINKER_PENDING_QUEUE, SentimentService.NEWS_LINKER_WORKING_QUEUE, 0)
                .repeat()
                .map(JsonObject::new)
                .map(SentimentJob::new)
                .forEach(this::startNewsLinkingJob);
    }

    private void startNewsLinkingJob(SentimentJob job) {
        LOG.info("Starting news linking for job: " + job.getJobId());

        EventBusService.<NewsLinkerService>getProxyObservable(serviceDiscovery, NewsLinkerService.class.getName())
                .flatMap(service -> doRateLimitedLinkingRequest(job.getNewsSearchResponse(), service))
                .subscribe(
                        result -> vertx.eventBus().send("news-linker:" + job.getJobId(), job.getNewsSearchResponse()),
                        failure -> vertx.eventBus().send("news-linker:" + job.getJobId(), new JsonObject().put("error", failure.getMessage())),
                        () -> LOG.info("Completed news linking job")
                );
    }

    private Observable<JsonObject> doRateLimitedLinkingRequest(JsonObject newsSearchResponse, NewsLinkerService service) {
        Observable<Object> articles = Observable.from(newsSearchResponse.getJsonArray("value"));
        Observable<Long> interval = Observable.interval(400, TimeUnit.MILLISECONDS);

        return Observable.zip(articles, interval, (observable, timer) -> observable)
                .map(json -> (JsonObject) json)
                .flatMap(json -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.linkEntities(json, observable.toHandler());
                    return observable.map(json::mergeIn);
                })
                .lastOrDefault(newsSearchResponse);
    }
}
