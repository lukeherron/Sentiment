package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.sentimentservice.job.CrawlerJob;
import com.gofish.sentiment.sentimentservice.queue.PendingQueue;
import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
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
import rx.Single;

/**
 * @author Luke Herron
 */
public class SentimentServiceImpl implements SentimentService {

    private static final Logger LOG = LoggerFactory.getLogger(SentimentServiceImpl.class);

    private final Vertx vertx;
    private final RedisClient redis;
    private final ServiceDiscovery serviceDiscovery;

    public SentimentServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;

        redis = RedisClient.create(vertx, new RedisOptions().setHost("redis"));
        serviceDiscovery = ServiceDiscovery.create(vertx);
    }

    @Override
    public SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        rxGetStorageService()
                .flatMap(service -> rxGetSentimentResults(query, service))
                .flatMap(sentimentResult -> {
                    if (sentimentResult.isEmpty()) {
                        // If the result is empty it will be because the query has not been added and analysed. We
                        // perform both of these steps and then return the results.
                        return rxAddNewQuery(query).flatMap(v ->
                                Single.create(new SingleOnSubscribeAdapter<>(fut -> analyseSentiment(query, fut))));
                    }
                    else {
                        return Single.just(sentimentResult);
                    }
                })
                .subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    @Override
    public SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        final CrawlerJob job = new CrawlerJob(query);

        RxHelper.toObservable(vertx.eventBus().<JsonObject>localConsumer("news-crawler:" + job.getJobId()))
                .map(Message::body)
                .subscribe(RxHelper.toSubscriber(resultHandler));

        // With the listeners ready, we can push the job onto the queue
        redis.lpush(PendingQueue.NEWS_CRAWLER.toString(), job.toJson().encode(), pushHandler -> {
            if (pushHandler.succeeded()) {
                LOG.info("Job added to queue: " + pushHandler.result());
            }
            else {
                LOG.error(pushHandler.cause().getMessage(), pushHandler.cause());
                resultHandler.handle(Future.failedFuture(pushHandler.cause()));
            }
        });

        return this;
    }

    /**
     *
     * @param query The new query to add
     * @return Single which emits the results of adding a new query to the StorageService
     */
    private Single<Void> rxAddNewQuery(String query) {

        return rxGetStorageService()
                .flatMap(service -> Single.create(new SingleOnSubscribeAdapter<Void>(fut ->
                        service.createCollection(query, fut))).map(r -> service))
                .flatMap(service -> {
                    final JsonObject collectionIndex = new JsonObject()
                            .put("name", 1)
                            .put("description", 1);

                    return Single.create(new SingleOnSubscribeAdapter<Void>(fut ->
                            service.createIndex(query, collectionIndex, fut)));
                });
    }

    /**
     *
     * @param query The query to search for and retrieve associated sentiment results
     * @param service StorageService to retrieve sentiment results from
     * @return Single which emits the stored sentiment results for the specified query
     */
    private Single<JsonObject> rxGetSentimentResults(String query, StorageService service) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut -> service.getSentimentResults(query, fut)));
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
}
