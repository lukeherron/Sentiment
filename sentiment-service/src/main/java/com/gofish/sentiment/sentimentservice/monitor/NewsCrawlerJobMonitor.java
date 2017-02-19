package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newscrawler.NewsCrawlerService;
import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.article.SentimentArticle;
import com.gofish.sentiment.sentimentservice.job.AnalyserJob;
import com.gofish.sentiment.sentimentservice.job.CrawlerJob;
import com.gofish.sentiment.sentimentservice.job.LinkerJob;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.redis.RedisTransaction;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import rx.Observable;

import java.util.Optional;
import java.util.UUID;

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
    protected void startJob(CrawlerJob job) {
        LOG.info("Starting news search for job: " + job.getJobId());

        // When pushing the job to our linker and analyser queues, those queues must have access to the news search result
        // in order to complete their portion of the work. This requires that the news search response is provided to the
        // job before it reaches those queues, but when we modify the job we can't use it to remove the job from the
        // news crawler working queue, as it will no longer match. For this reason, we create a copy before we modify it
        // so that we can successfully remove it.
        CrawlerJob originalJob = new CrawlerJob(job.toJson());
        RedisTransaction transaction = redis.transaction();

        EventBusService.<NewsCrawlerService>getProxyObservable(serviceDiscovery, NewsCrawlerService.class.getName())
                .flatMap(service -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.crawlQuery(job.getQuery(), observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return observable;
                })
                .doOnNext(job::setResult)
                .flatMap(result -> Observable.from(result.getJsonArray("value")))
                .map(article -> (JsonObject) article)
                // Assign a unique UUID to each article for tracking
                .map(article -> article.put("SentimentUUID", UUID.randomUUID().toString()))
                // Send each article to the NewsLinker and NewsAnalyser job queues
                .map(SentimentArticle::new)
                .map(article -> {
                    transaction.multiObservable()
                            .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_LINKER.toString(), new LinkerJob(article).encode()))
                            .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_ANALYSER.toString(), new AnalyserJob(article).encode()))
                            .flatMap(x -> transaction.execObservable())
                            .doOnError(error -> {
                                LOG.error(error.getMessage(), error);
                                transaction.discardObservable();
                            });

                    return article;
                })
                // Listen for the results of the NewsLinker and NewsAnalyser jobs and call zip to merge the results
                // together. This will produce a completed crawl result for the specific article.
                .flatMap(article -> {
                    final ObservableFuture<AnalyserJob> newsAnalyserFuture = RxHelper.observableFuture();
                    final String analyserAddress = "news-analyser:article:" + article.getUUID();
                    vertx.eventBus().<JsonObject>localConsumer(analyserAddress, messageHandler -> {
                        final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
                        newsAnalyserFuture.toHandler().handle(Future.succeededFuture(new AnalyserJob(result)));
                    });

                    final ObservableFuture<LinkerJob> newsLinkerFuture = RxHelper.observableFuture();
                    final String linkerAddress = "news-linker:article:" + article.getUUID();
                    vertx.eventBus().<JsonObject>localConsumer(linkerAddress, messageHandler -> {
                        final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
                        newsLinkerFuture.toHandler().handle(Future.succeededFuture(new LinkerJob(result)));
                    });

                    return Observable.zip(newsAnalyserFuture, newsLinkerFuture, (analyserJob, linkerJob) ->
                            new SentimentArticle(new JsonObject().mergeIn(analyserJob.getResult()).mergeIn(linkerJob.getResult())));
                })
                .doOnNext(LOG::info)
                // With the results merged together, we now need to update the original article with the final result
                .map(article -> {
                    job.getResult().getJsonArray("value").stream()
                            .map(originalArticle -> (JsonObject) originalArticle)
                            .filter(originalArticle -> originalArticle.getString("SentimentUUID").equals(article.getUUID()))
                            .forEach(originalArticle -> originalArticle.mergeIn(article.toJson()));

                    return job;
                })
                // Once all article have been processed, we can remove the news crawler job from its queue
                .lastOrDefault(job)
                //.flatMap(v -> redis.lremObservable(workingQueue.toString(), 0, originalJob.toJson().encode()))
                .subscribe(
                        result -> processCompletedJob(workingQueue, pendingQueue, result, result.getResult()),
                        failure -> processFailedJob(workingQueue, pendingQueue, job, failure),
                        () -> LOG.info("Completed news search crawl for job: " + job.getJobId())
                );
    }

    @Override
    protected void announceJobResult(CrawlerJob job) {
        vertx.eventBus().send("news-crawler:" + job.getJobId(), job.toJson());
    }
}
