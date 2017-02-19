package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newslinker.NewsLinkerService;
import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.job.CrawlerJob;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import rx.Observable;

import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class NewsLinkerJobMonitor extends AbstractJobMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLinkerJobMonitor.class);
    private static final PendingQueue pendingQueue = PendingQueue.NEWS_LINKER;
    private static final WorkingQueue workingQueue = WorkingQueue.NEWS_LINKER;

    @Override
    protected PendingQueue getPendingQueue() {
        return pendingQueue;
    }

    @Override
    protected WorkingQueue getWorkingQueue() {
        return workingQueue;
    }

    @Override
    protected void startJob(CrawlerJob job) {
        LOG.info("Starting news linking for job: " + job.getJobId());

        EventBusService.<NewsLinkerService>getProxyObservable(serviceDiscovery, NewsLinkerService.class.getName())
                .flatMap(service -> startRateLimitedRequest(job.getNewsSearchResponse(), service))
                .subscribe(
                        result -> processCompletedJob(workingQueue, pendingQueue, job, result),
                        failure -> processFailedJob(workingQueue, pendingQueue, job, failure),
                        () -> LOG.info("Completed news linking job"));
    }

    @Override
    protected void setJobResult(CrawlerJob job, JsonObject jobResult) {
        job.setEntityLinkingResponse(jobResult);
    }

    @Override
    protected void announceJobResult(CrawlerJob job) {
        vertx.eventBus().send("news-linker:" + job.getJobId(), job.toJson());
    }

    private Observable<JsonObject> startRateLimitedRequest(JsonObject newsSearchResponse, NewsLinkerService service) {
        // Avoid modifying the original job until we are certain the request is going to be successful (i.e. the job has
        // been removed from any active queues first). To avoid this, we make a copy of newsSearchResponse and make
        // changes to the copy. This is necessary because newsSearchResponse is embedded in the original job, and any
        // changes to the JsonObject are reflected in the job
        JsonObject workingCopy = newsSearchResponse.copy();

        Observable<Object> articles = Observable.from(workingCopy.getJsonArray("value"));
        Observable<Long> interval = Observable.interval(400, TimeUnit.MILLISECONDS);

        return Observable.zip(articles, interval, (observable, timer) -> observable)
                .map(json -> (JsonObject) json)
                .flatMap(json -> getLinkEntitiesObservable(service, json))
                .lastOrDefault(null)
                .map(v -> workingCopy); // We want the entire response, not just the last processed article
    }

    private Observable<JsonObject> getLinkEntitiesObservable(NewsLinkerService service, JsonObject json) {
        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
        service.linkEntities(json, observable.toHandler());
        ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
        return observable.map(json::mergeIn);
    }
}
