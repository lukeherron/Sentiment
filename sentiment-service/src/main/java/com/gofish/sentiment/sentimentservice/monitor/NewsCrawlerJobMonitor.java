package com.gofish.sentiment.sentimentservice.monitor;

import com.gofish.sentiment.newscrawler.NewsCrawlerService;
import com.gofish.sentiment.sentimentservice.article.SentimentArticle;
import com.gofish.sentiment.sentimentservice.job.*;
import com.gofish.sentiment.sentimentservice.queue.PendingQueue;
import com.gofish.sentiment.sentimentservice.queue.WorkingQueue;
import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisOptions;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.redis.RedisClient;
import io.vertx.rxjava.redis.RedisTransaction;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.ServiceReference;
import rx.Observable;
import rx.Single;

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
        final CrawlerJob job = new CrawlerJob(jsonJob);

        // When pushing the job to our linker and analyser queues, those queues must have access to the news search result
        // in order to complete their portion of the work. This requires that the news search response is provided to the
        // job before it reaches those queues, but when we modify the job we can't use it to remove the job from the
        // news crawler working queue, as it will no longer match. For this reason, we create a copy before we modify it
        // so that we can successfully remove it.
        final CrawlerJob original = job.copy();
        job.setState(Job.State.ACTIVE);

        String query = job.getPayload().getString("query");

        LOG.info("Starting news search for job: " + job.getJobId());
        rxGetNewsCrawlerService()
                .flatMap(service -> Single.create(new SingleOnSubscribeAdapter<JsonObject>(handler ->
                        service.crawlQuery(query, handler)))
                        .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service)))
                .doOnSuccess(job::setResult)
                .flatMapObservable(result -> rxFilterExistingArticles(query, result))
                .concatMap(this::rxMergeJobResults)
                .map(SentimentArticle::toJson)
                .toList()
                .map(JsonArray::new)
                .subscribe(
                        result -> {
                            job.getResult().remove("value");
                            job.getResult().put("value", result);
                            processCompletedJob(original, job.getResult());
                        },
                        failure -> processFailedJob(original, failure),
                        () -> LOG.info("Completed news search crawl for job: " + job.getJobId()));
    }

    private Observable<SentimentArticle> rxFilterExistingArticles(String query, JsonObject result) {
        return Observable.from(result.getJsonArray("value"))
                .map(article -> (JsonObject) article)
                .map(SentimentArticle::new)
                .flatMap(article -> rxGetStorageService()
                        .flatMap(service -> Single.create(new SingleOnSubscribeAdapter<Boolean>(handler ->
                                service.hasArticle(query, article.getName(), article.getDescription(), handler)))
                                .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service)))
                        .toObservable()
                        .filter(hasArticle -> !hasArticle)
                        .map(hasArticle -> article));
    }

    private Observable<SentimentArticle> rxMergeJobResults(SentimentArticle article) {
        RedisTransaction transaction = actionClient.transaction();
        LOG.info(article.getUUID());

        final ObservableFuture<AnalyserJob> newsAnalyser = rxGetNewsAnalysis(article);
        final ObservableFuture<LinkerJob> newsLinker = rxGetNewsLinking(article);

        transaction.rxMulti()
                .flatMap(x -> transaction.rxLpush(PendingQueue.NEWS_ANALYSER.toString(), new AnalyserJob(article).encode()))
                .flatMap(x -> transaction.rxLpush(PendingQueue.NEWS_LINKER.toString(), new LinkerJob(article).encode()))
                .flatMap(x -> transaction.rxExec())
                .doOnError(error -> {
                    LOG.error(error);
                    transaction.rxDiscard();
                })
                .subscribe(
                        result -> LOG.info("Pushed linker and analyser job to pending queues: " + result.encodePrettily()),
                        failure -> LOG.error(failure.getMessage(), failure)
                );

        return Observable.zip(newsAnalyser, newsLinker, ((analyserJob, linkerJob) -> {
            JsonObject mergedArticle = new JsonObject().mergeIn(analyserJob.getResult()).mergeIn(linkerJob.getResult());
            return new SentimentArticle(mergedArticle);
        }));
    }

    /**
     * Searches the cluster for the service record relating to the Storage module
     * @return Single which emits the Storage service published to the cluster
     */
    private Single<StorageService> rxGetStorageService() {
        return serviceDiscovery.rxGetRecord(record -> record.getName().equals(StorageService.NAME))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<StorageService>get);
    }

    private Single<NewsCrawlerService> rxGetNewsCrawlerService() {
        return serviceDiscovery.rxGetRecord(record -> record.getName().equals(NewsCrawlerService.NAME))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<NewsCrawlerService>get);
    }

    private ObservableFuture<AnalyserJob> rxGetNewsAnalysis(SentimentArticle article) {
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

    private ObservableFuture<LinkerJob> rxGetNewsLinking(SentimentArticle article) {

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

    private void processCompletedJob(CrawlerJob job, JsonObject result) {
        LOG.info("Processing of job " + job.getJobId() + " in " + workingQueue + " complete");

        // Save the results to persistent storage and remove the job from the queue
        String query = job.getPayload().getString("query");
        rxGetStorageService()
                .flatMap(service -> Single.create(new SingleOnSubscribeAdapter<JsonObject>(handler ->
                        service.saveArticles(query, result.getJsonArray("value"), handler)))
                        .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service)))
                .doOnSuccess(LOG::info)
                .flatMap(saveResult -> actionClient.rxLrem(workingQueue.toString(), 0, job.encode()))
                .subscribe(removeResult -> {
                    job.setResult(result);
                    job.setState(Job.State.COMPLETE);
                    announceJobResult(job);
                    LOG.info("Finished processing completed job in queue: " + workingQueue);
                }, failure -> LOG.error(failure.getMessage(), failure));
    }

    private void processFailedJob(CrawlerJob job, Throwable error) {
        LOG.error("Failed to process job: " + job.getJobId(), error);

        CrawlerJob original = job.copy();
        job.incrementAttempts(); // Important to set this as it determines the fallback timeout based on retry attempts
        RetryStrategyFactory.calculate(job, error);

        RedisTransaction transaction = actionClient.transaction();
        transaction.rxMulti().delay(job.getTimeout(), TimeUnit.MILLISECONDS)
                .flatMap(x -> transaction.rxLrem(WorkingQueue.NEWS_CRAWLER.toString(), 0, original.encode()))
                .flatMap(x -> transaction.rxLpush(PendingQueue.NEWS_CRAWLER.toString(), job.encode()))
                .flatMap(x -> transaction.rxExec())
                .subscribe(
                        result -> LOG.info("Re-queued failed crawler job: " + result),
                        failure -> transaction.rxDiscard());

        // TODO: should this call be made in the onSuccess of the above subscribe method?
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
