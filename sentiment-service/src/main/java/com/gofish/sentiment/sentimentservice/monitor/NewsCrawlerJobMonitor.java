package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newscrawler.NewsCrawlerService;
import com.gofish.sentiment.sentimentservice.PendingQueue;
import com.gofish.sentiment.sentimentservice.WorkingQueue;
import com.gofish.sentiment.sentimentservice.article.SentimentArticle;
import com.gofish.sentiment.sentimentservice.job.*;
import com.gofish.sentiment.storage.StorageService;
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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class NewsCrawlerJobMonitor extends AbstractVerticle {

    private static final PendingQueue pendingQueue = PendingQueue.NEWS_CRAWLER;
    private static final WorkingQueue workingQueue = WorkingQueue.NEWS_CRAWLER;
    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerJobMonitor.class);

    private RedisClient actionClient;
    private RedisClient monitorClient;
    private RedisOptions redisOptions = new RedisOptions().setHost("redis");
    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        actionClient = RedisClient.create(vertx, redisOptions);
        monitorClient = RedisClient.create(vertx, redisOptions);
        serviceDiscovery = ServiceDiscovery.create(vertx);

        actionClient.ping(resultHandler -> {
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
                .subscribe(this::startJob, LOG::error, () -> LOG.info("COMPLETED"));
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
        CrawlerJob job = new CrawlerJob(jsonJob);
        LOG.info("Starting news search for job: " + job.getJobId());

        // When pushing the job to our linker and analyser queues, those queues must have access to the news search result
        // in order to complete their portion of the work. This requires that the news search response is provided to the
        // job before it reaches those queues, but when we modify the job we can't use it to remove the job from the
        // news crawler working queue, as it will no longer match. For this reason, we create a copy before we modify it
        // so that we can successfully remove it.
        CrawlerJob originalJob = new CrawlerJob(job.toJson());
        RedisTransaction transaction = actionClient.transaction();

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
                .map(SentimentArticle::new)
                .concatMap(article -> {
                    LOG.info(article.getUUID());

                    final ObservableFuture<AnalyserJob> newsAnalyser = getNewsAnalyserObservable(article);
                    final ObservableFuture<LinkerJob> newsLinker = getNewsLinkerObservable(article);

                    transaction.multiObservable()
                            .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_ANALYSER.toString(), new AnalyserJob(article).encode()))
                            .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_LINKER.toString(), new LinkerJob(article).encode()))
                            .flatMap(x -> transaction.execObservable())
                            .doOnError(error -> {
                                LOG.error(error);
                                transaction.discardObservable();
                            })
                            .subscribe(
                                    result -> LOG.info("Pushed linker job and analyser job to pending queues: " + result.encodePrettily()),
                                    failure -> LOG.error(failure.getMessage(), failure),
                                    () -> LOG.info("Job transfers complete"));

                    return Observable.zip(newsAnalyser, newsLinker, (analyserJob, linkerJob) ->
                            new SentimentArticle(new JsonObject().mergeIn(analyserJob.getResult()).mergeIn(linkerJob.getResult())));
                })
                .doOnNext(article -> LOG.info("Completed merging analyser and linking job results. Merging updated article back into original"))
                .map(article -> {
                    job.getResult().getJsonArray("value").stream()
                            .map(originalArticle -> (JsonObject) originalArticle)
                            .map(SentimentArticle::new)
                            .filter(originalArticle -> originalArticle.getUUID().equals(article.getUUID()))
                            .forEach(originalArticle -> originalArticle.mergeIn(article.toJson()));

                    return job;
                })
                .lastOrDefault(job)
                .doOnNext(crawlerJob -> LOG.info("All articles processed"))
                .subscribe(
                        result -> processCompletedJob(workingQueue, originalJob, result.getResult()),
                        failure -> processFailedJob(originalJob, failure),
                        () -> LOG.info("Completed news search crawl for job: " + job.getJobId())
                );
    }

    private ObservableFuture<AnalyserJob> getNewsAnalyserObservable(SentimentArticle article) {
        final ObservableFuture<AnalyserJob> newsAnalyserFuture = RxHelper.observableFuture();
        final String analyserSuccessAddress = "news-analyser:article:" + article.getUUID();

        MessageConsumer<JsonObject> analyseSuccessConsumer = vertx.eventBus().localConsumer(analyserSuccessAddress, messageHandler -> {
            LOG.info("Received response from NewsAnalyser");
            final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            newsAnalyserFuture.toHandler().handle(Future.succeededFuture(new AnalyserJob(result)));
        });

        analyseSuccessConsumer.exceptionHandler(throwable -> {
            LOG.error(throwable.getMessage(), throwable);
            newsAnalyserFuture.toHandler().handle(Future.failedFuture(throwable));
        }).endHandler(v -> analyseSuccessConsumer.unregister());

//        final String analyseFailAddress = "news-analyser:article:error:" + article.getUUID();
//        MessageConsumer<JsonObject> analyseFailConsumer = vertx.eventBus().localConsumer(analyseFailAddress, messageHandler -> {
//            final JsonObject message = messageHandler.body();
//            final JsonObject retryStrategy = message.getJsonObject("retryStrategy");
//        });

//        analyseFailConsumer.exceptionHandler(throwable -> {
//            LOG.error(throwable.getMessage(), throwable);
//            newsAnalyserFuture.toHandler().handle(Future.failedFuture(throwable));
//        }).endHandler(v -> analyseFailConsumer.unregister());

        return newsAnalyserFuture;
    }

    private ObservableFuture<LinkerJob> getNewsLinkerObservable(SentimentArticle article) {
        final ObservableFuture<LinkerJob> newsLinkerFuture = RxHelper.observableFuture();

        final String linkerSuccessAddress = "news-linker:article:" + article.getUUID();
        MessageConsumer<JsonObject> linkerSuccessConsumer = vertx.eventBus().localConsumer(linkerSuccessAddress, messageHandler -> {
            LOG.info("Received response from NewsLinker");
            final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            newsLinkerFuture.toHandler().handle(Future.succeededFuture(new LinkerJob(result)));
        });

        linkerSuccessConsumer.exceptionHandler(throwable -> {
            LOG.error(throwable.getMessage(), throwable);
            newsLinkerFuture.toHandler().handle(Future.failedFuture(throwable));
        }).endHandler(v -> linkerSuccessConsumer.unregister());

//        final String linkerFailAddress = "news-linker:article:error:" + article.getUUID();
//        MessageConsumer<JsonObject> linkerFailConsumer = vertx.eventBus().localConsumer(linkerFailAddress, messageHandler -> {
//            final JsonObject message = messageHandler.body();
//            final JsonObject retryStrategy = message.getJsonObject("retryStrategy");
//        });

//        linkerFailConsumer.exceptionHandler(throwable -> {
//            LOG.error(throwable.getMessage(), throwable);
//            newsLinkerFuture.toHandler().handle(Future.failedFuture(throwable));
//        }).endHandler(v -> linkerFailConsumer.unregister());

        return newsLinkerFuture;
    }

    private void processCompletedJob(WorkingQueue workingQueue, CrawlerJob job, JsonObject jobResult) {
        LOG.info("Processing of job " + job.getJobId() + " in " + workingQueue + " complete");

        // Save the results to persistent storage and remove the job from the queue
        EventBusService.<StorageService>getProxyObservable(serviceDiscovery, StorageService.class.getName())
                .flatMap(service -> {
                    ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
                    service.saveArticles(job.getQuery(), jobResult.getJsonArray("value"), observable.toHandler());
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return observable;
                })
                .doOnNext(LOG::info)
                .flatMap(saveResult -> actionClient.lremObservable(workingQueue.toString(), 0, job.toJson().encode()))
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

    private void processFailedJob(CrawlerJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId(), error);

        Job originalJob = job.copy(); // We need to make a copy to ensure redis can find the original job in the working queue
        job.incrementAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        RedisTransaction transaction = actionClient.transaction();

        transaction.multiObservable().delay(job.getTimeout(), TimeUnit.MILLISECONDS)
                .flatMap(x -> transaction.lremObservable(WorkingQueue.NEWS_CRAWLER.toString(), 0, originalJob.encode()))
                .flatMap(x -> transaction.lpushObservable(PendingQueue.NEWS_CRAWLER.toString(), job.encode()))
                .flatMap(x -> transaction.execObservable())
                .subscribe(
                        result -> LOG.info(result.encodePrettily()),
                        failure -> transaction.discardObservable(),
                        () -> LOG.info("Re-queued news crawler job"));

        announceJobResult(job, error);
    }

    private void announceJobResult(Job job) {
        vertx.eventBus().publish("news-crawler:" + job.getJobId(), job.toJson());
    }

    private void announceJobResult(Job job, Throwable error) {
        vertx.eventBus().publish("news-crawler:error" + job.getJobId(), new JsonObject()
                .put("error", error.getMessage())
                .put("retryStrategy", job.getRetryStrategy()));
    }
}
