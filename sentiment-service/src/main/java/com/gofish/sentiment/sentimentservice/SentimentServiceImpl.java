package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.sentimentservice.job.CrawlerJob;
import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;

import java.util.Optional;

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
        final Future<JsonObject> sentimentFuture = Future.future();

        getStorageService().compose(storageService ->
                        storageService.getSentimentResults(query, sentimentFuture.completer()), sentimentFuture);

        sentimentFuture.setHandler(sentimentHandler -> {
            if (sentimentHandler.succeeded()) {
                final JsonObject sentimentResult = sentimentHandler.result();
                if (sentimentResult.isEmpty()) {
                    // If the result is empty it will be because the query has not been added and analysed. We perform
                    // both of these steps and then return the results.
                    addNewQuery(query)
                            .compose(v -> {
                                final Future<JsonObject> sentimentResultsFuture = Future.future();
                                this.analyseSentiment(query, sentimentResultsFuture.completer());
                                return sentimentResultsFuture;
                            })
                            .setHandler(resultHandler);
                }
                else {
                    resultHandler.handle(Future.succeededFuture(sentimentResult));
                }
            }
            else {
                resultHandler.handle(Future.failedFuture(sentimentHandler.cause()));
            }
        });

        return this;
    }

    @Override
    public SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        final CrawlerJob job = new CrawlerJob(query);

        vertx.eventBus().<JsonObject>localConsumer("news-crawler:" + job.getJobId(), messageHandler -> {
            final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            resultHandler.handle(Future.succeededFuture(result));
        });

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

    private Future<Void> addNewQuery(String query) {
        final Future<Void> addNewQueryFuture = Future.future();
        final Future<Void> createCollectionFuture = Future.future();
        final Future<Void> createIndexFuture = Future.future();

        // Start by creating a collection in the DB named after the query, then create an index for that collection
        getStorageService()
                .compose(storageService -> {
                    final StorageService service = storageService.createCollection(query, createCollectionFuture.completer());
                    return Future.succeededFuture(service);
                })
                .compose(storageService -> {
                    final JsonObject collectionIndex = new JsonObject()
                            .put("name", 1)
                            //.put("datePublished", 1)
                            .put("description", 1);

                    storageService.createIndex(query, collectionIndex, createIndexFuture.completer());
                    return createIndexFuture;
                })
                .setHandler(addNewQueryFuture.completer());

        return addNewQueryFuture;
    }

    private Future<StorageService> getStorageService() {
        final Future<StorageService> storageServiceFuture = Future.future();
        EventBusService.getProxy(serviceDiscovery, StorageService.class, storageServiceFuture.completer());

        return storageServiceFuture;
    }
}
