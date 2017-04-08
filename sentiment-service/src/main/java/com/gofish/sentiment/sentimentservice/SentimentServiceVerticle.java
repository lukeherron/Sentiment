package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.sentimentservice.monitor.NewsAnalyserJobMonitor;
import com.gofish.sentiment.sentimentservice.monitor.NewsCrawlerJobMonitor;
import com.gofish.sentiment.sentimentservice.monitor.NewsLinkerJobMonitor;
import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceReference;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ProxyHelper;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Luke Herron
 */
public class SentimentServiceVerticle extends AbstractVerticle {

    private static final int DEFAULT_TIMER_DELAY = 3600000;
    private static final Logger LOG = LoggerFactory.getLogger(SentimentServiceVerticle.class);

    private JsonObject config;
    private SentimentService sentimentService;
    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up SentimentService verticle");

        this.config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load sentiment service configuration"));

        Future<Record> publishFuture = Future.future();
        Future<String> jobQueueFuture = Future.future();

        sentimentService = SentimentService.create(vertx, config);
        messageConsumer = ProxyHelper.registerService(SentimentService.class, vertx, sentimentService, SentimentService.ADDRESS);
        serviceDiscovery = ServiceDiscovery.create(vertx);
        record = EventBusService.createRecord(SentimentService.NAME, SentimentService.ADDRESS, SentimentService.class);

        serviceDiscovery.publish(record, publishFuture.completer());

        // NewsCrawler workers immediately start polling the redis job queue on launch, so ensure that the redis service
        // is actually up and running (and responds to a ping) before launching the workers.
        RedisClient.create(vertx, new RedisOptions().setHost("redis")).ping(jobQueueFuture.completer());

        // Once we know this verticles service has been announced to the cluster and our job queue is up and running,
        // we can go ahead and deploy the workers that monitor our queues and the timed crawler which will start
        // generating jobs and putting them into the queues
        CompositeFuture.all(publishFuture, jobQueueFuture).setHandler(resultHandler ->
                deployQueueMonitors()
                        .doOnCompleted(this::startPeriodicCrawl)
                        .subscribe(RxHelper.toSubscriber(startFuture.completer())));
    }

    private Completable deployQueueMonitors() {
        // Deploy worker verticles which poll each pending job queue
        int instances = Runtime.getRuntime().availableProcessors();
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config).setWorker(true).setInstances(instances);
        List<Future> deployFutures = new ArrayList<>();

        Arrays.asList(
                NewsCrawlerJobMonitor.class.getName(),
                NewsLinkerJobMonitor.class.getName(),
                NewsAnalyserJobMonitor.class.getName()
        ).forEach(verticleWorker -> {
            Future<String> deployFuture = Future.future();
            deployFutures.add(deployFuture);
            vertx.deployVerticle(verticleWorker, deploymentOptions, deployFuture.completer());
        });

        return Single.create(new SingleOnSubscribeAdapter<CompositeFuture>(fut ->
                CompositeFuture.all(deployFutures).setHandler(fut))).toCompletable();
    }

    private void startPeriodicCrawl() {
        RxHelper.toObservable(vertx.periodicStream(config.getInteger("timer.delay", DEFAULT_TIMER_DELAY)))
                .flatMapSingle(id -> rxGetStorageService())
                .retryWhen(this::rxGetRetryStrategy)
                .flatMapSingle(this::rxGetCollections)
                .flatMap(Observable::from)
                .map(query -> (String) query)
                .flatMapSingle(this::rxStartAnalysis)
                .subscribe(
                        result -> LOG.info("Queued crawl request for query: " + result),
                        failure -> LOG.error(failure),
                        () -> LOG.info("Periodic crawl is complete"));
    }

    /**
     * Retrieves a list of all current collections from the Storage service
     * @return Single which emits all available collection found in Storage service
     */
    private Single<JsonArray> rxGetCollections(StorageService service) {
        return Single.create(new SingleOnSubscribeAdapter<>(service::getCollections))
                .doOnEach(notification -> ServiceDiscovery.releaseServiceObject(serviceDiscovery, service));
    }

    /**
     * Searches the cluster for the service record relating to the Storage module
     * @return Single which emits the Storage service published to the cluster
     */
    private Single<StorageService> rxGetStorageService() {

        return Single.create(new SingleOnSubscribeAdapter<Record>(fut ->
                serviceDiscovery.getRecord(record -> record.getName().equals(StorageService.NAME), fut)))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<StorageService>get);
    }

    /**
     * Searches the cluster for the service record relating to the SentimentService module
     * @return Single which emits the SentimentService service published to the cluster
     */
    private Single<SentimentService> rxGetSentimentService() {

        return Single.create(new SingleOnSubscribeAdapter<Record>(fut ->
                serviceDiscovery.getRecord(record -> record.getName().equals(SentimentService.NAME), fut)))
                .map(serviceDiscovery::getReference)
                .map(ServiceReference::<SentimentService>get);
    }

    private Observable<Long> rxGetRetryStrategy(Observable<? extends Throwable> attempts) {
        return attempts.zipWith(Observable.range(1, 100), (n, i) -> i)
                .flatMap(i -> Observable.timer(i, TimeUnit.SECONDS));
    }

    private Single<String> rxStartAnalysis(String query) {
        return rxGetSentimentService()
                // We aren't concerned with returning results from the analysis, as we won't be using them
                // i.e. we only want to queue the jobs and let them run on their own. We provide a handler
                // out of necessity but we do not wait on it and simply return the original query
                .map(service -> service.analyseSentiment(query, resultHandler -> {}))
                .map(service -> {
                    ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
                    return query;
                });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        Future<Void> recordUnpublishFuture = Future.future();
        Future<Void> messageConsumerUnregisterFuture = Future.future();

        serviceDiscovery.unpublish(record.getRegistration(), recordUnpublishFuture.completer());
        messageConsumer.unregister(messageConsumerUnregisterFuture.completer());

        recordUnpublishFuture.compose(v -> {
            serviceDiscovery.close();
            return messageConsumerUnregisterFuture;
        }).setHandler(stopFuture.completer());
    }
}
