package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.newsanalyser.rxjava.NewsAnalyserService;
import com.gofish.sentiment.newscrawler.rxjava.NewsCrawlerService;
import com.gofish.sentiment.newslinker.NewsLinkerService;
import com.gofish.sentiment.storage.rxjava.StorageService;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.Record;
import io.vertx.serviceproxy.ProxyHelper;
import rx.Observable;
import rx.Single;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Luke Herron
 */
public class SentimentServiceVerticle extends AbstractVerticle {

    private static final int DEFAULT_TIMER_DELAY = 3600000;
    private static final Logger LOG = LoggerFactory.getLogger(SentimentServiceVerticle.class);

    private JsonObject config;
    private com.gofish.sentiment.sentimentservice.rxjava.SentimentService sentimentService;
    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up SentimentService verticle");

        this.config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load sentiment service configuration"));

        sentimentService = com.gofish.sentiment.sentimentservice.rxjava.SentimentService.create(vertx, config);
        messageConsumer = ProxyHelper.registerService(SentimentService.class, vertx.getDelegate(), sentimentService.getDelegate(), SentimentService.ADDRESS);

        // List the service dependencies that are required for this service to perform its operations successfully
        // TODO: move this into the vertx configuration i.e. add it to external configuration which is loaded with the verticle
        JsonArray serviceDependencies = new JsonArray()
                .add(NewsAnalyserService.name())
                .add(NewsCrawlerService.name())
                .add(NewsLinkerService.name())
                .add(StorageService.name());

        serviceDiscovery = ServiceDiscovery.create(vertx, serviceDiscovery -> {
            LOG.info("Service Discovery initialised");
            record = EventBusService.createRecord(SentimentService.NAME, SentimentService.ADDRESS, SentimentService.class.getName());

            // Publish the service
            serviceDiscovery.rxPublish(record).subscribe(LOG::info, LOG::error);

            // and wait for service dependencies to be available before marking the verticle as started
            serviceDiscovery.rxGetRecords((JsonObject) null)
                    .doOnSuccess(r -> LOG.info("Searching for service dependencies"))
                    .map(records -> records.stream().map(Record::getName).collect(Collectors.toList()).containsAll(serviceDependencies.getList()))
                    .flatMap(dependenciesMet -> dependenciesMet ? Single.<Void>just(null) : Single.error(new Throwable()))
                    .retryWhen(errors -> errors.flatMap(error -> Observable.timer(5, TimeUnit.SECONDS)))
                    .doOnSuccess(v -> startPeriodicCrawl())
                    .subscribe(RxHelper.toSubscriber(startFuture));
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        ObservableFuture<Void> messageConsumerObservable = new ObservableFuture<>();
        serviceDiscovery.rxUnpublish(record.getRegistration())
                .flatMapObservable(v -> {
                    messageConsumer.unregister(messageConsumerObservable.toHandler());
                    return messageConsumerObservable;
                })
                .doOnNext(v -> serviceDiscovery.close())
                .subscribe(RxHelper.toSubscriber(stopFuture));
    }

    /**
     * Starts the news crawler and analysis, which continually repeats at the set interval
     */
    private void startPeriodicCrawl() {
        vertx.periodicStream(config.getInteger("timer.delay", DEFAULT_TIMER_DELAY))
                .toObservable()
                .flatMapSingle(id -> rxGetCrawlData())
                .flatMap(Observable::from).cast(String.class)
                .flatMapSingle(query -> sentimentService.rxAnalyseSentiment(query))
                .onErrorResumeNext(error -> {
                    error.printStackTrace();
                    return Observable.just(new JsonObject());
                })
                .subscribe(LOG::info, LOG::error, () -> LOG.info("Periodic crawl complete"));
    }

    /**
     * Retrieves each collection from storage, each collection name represents the news search query i.e. the crawl data
     * @return Single that emits the list of collections in storage
     */
    private Single<JsonArray> rxGetCrawlData() {

        return rxGetService(StorageService.name(), StorageService.class)
                .flatMap(service -> service.rxGetCollections()
                        .doOnEach(notifications -> release(service)));
    }

    /**
     * Helper method for retrieving a service from service discovery, wrapped in a Single so that it can be easily
     * utilised in an rx chain.
     * @param recordName The name of the record to filter available services by.
     * @param clazz The client class of the expected service.
     * @param <T> The type of the client class. This can be alternated between the rxjava or non-rx service class.
     * @return Single that emits the located service based on the recordName, if any.
     */
    private <T> Single<T> rxGetService(String recordName, Class<T> clazz) {

        return Single.create(new SingleOnSubscribeAdapter<T>(fut ->
                EventBusService.getServiceProxy(serviceDiscovery, record -> record.getName().equals(recordName), clazz, fut)
        ));
    }

    /**
     * Releases the provided service object from ServiceDiscovery.
     * @param service The service object to be released.
     */
    private void release(Object service) {
        ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
    }
}
