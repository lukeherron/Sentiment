package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.storage.StorageService;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
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
        Future<JsonObject> sentimentFuture = Future.future();

        getStorageService().compose(storageService ->
                        storageService.getSentimentResults(query, sentimentFuture.completer()),
                sentimentFuture);

        sentimentFuture.setHandler(sentimentHandler -> {
            JsonObject sentimentResult = sentimentHandler.result();
            if (sentimentHandler.succeeded() && sentimentResult != null) {
                resultHandler.handle(Future.succeededFuture(sentimentResult));
            }
            else if (sentimentHandler.succeeded() && sentimentResult == null){
                // Create the collection and run a new crawl job to analyse the sentiment
                addNewQuery(query).compose(v -> analyseSentiment(query)).setHandler(resultHandler);
            }
            else {
                resultHandler.handle(Future.failedFuture(sentimentHandler.cause()));
            }
        });

        return this;
    }

    private Future<Void> addNewQuery(String query) {
        Future<Void> addNewQueryFuture = Future.future();
        Future<Void> createCollectionFuture = Future.future();
        Future<Void> createIndexFuture = Future.future();

        // Start by creating a collection in the DB named after the query, then create an index for that collection
        getStorageService()
                .compose(storageService -> {
                    storageService.createCollection(query, createCollectionFuture.completer());
                    return getStorageService();
                })
                .compose(storageService -> {
                    final JsonObject collectionIndex = new JsonObject()
                            .put("name", 1)
                            .put("datePublished", 1)
                            .put("description", 1);

                    storageService.createIndex(query, collectionIndex, createIndexFuture.completer());
                    return createIndexFuture;
                })
                .setHandler(addNewQueryFuture.completer());

        return addNewQueryFuture;
    }

    private Future<JsonObject> analyseSentiment(String query) {
        Future<JsonObject> newsAnalyserFuture = Future.future();
        Future<JsonObject> newsLinkerFuture = Future.future();
        CompositeFuture analyseSentimentFuture = CompositeFuture.join(newsAnalyserFuture, newsLinkerFuture);

        SentimentJob job = new SentimentJob(query);

        // Set up a unique listener to receive the results of a processed job. There are three specific job queues
        // (although each are split into pending/working), but we only need to listen for two: news-analyser and
        // news-linker, as both of these queues work on the results of the news-crawler job queue, i.e. we know we can't
        // receive the final result from the news-crawler, so we exclude listening for its results.
        MessageConsumer<JsonObject> analyseConsumer = vertx.eventBus().localConsumer("news-analyser:" + job.getJobId(), messageHandler -> {
            JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            newsAnalyserFuture.complete(result);
        });

        MessageConsumer<JsonObject> linkerConsumer = vertx.eventBus().localConsumer("news-linker:" + job.getJobId(), messageHandler -> {
            JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            newsLinkerFuture.complete(result);
        });

        analyseConsumer.exceptionHandler(newsAnalyserFuture::fail);
        linkerConsumer.exceptionHandler(newsLinkerFuture::fail);
        analyseConsumer.endHandler(endHandler -> analyseConsumer.unregister());
        linkerConsumer.endHandler(endHandler -> linkerConsumer.unregister());

        // With the listeners ready, we can push the job onto the queue
        redis.lpush(SentimentService.NEWSCRAWLER_PENDING, job.toJson().encode(), pushHandler -> {
            if (pushHandler.succeeded()) {
                LOG.info("Job added to queue: " + pushHandler.result());
            }
            else {
                analyseSentimentFuture.fail(pushHandler.cause());
            }
        });

        return analyseSentimentFuture.map(future -> mergeFutureResults(newsAnalyserFuture, newsLinkerFuture));
    }

    private JsonObject mergeFutureResults(Future<JsonObject> newsAnalyserFuture, Future<JsonObject> newsLinkerFuture) {
        JsonObject analysisJson = newsAnalyserFuture.result();
        JsonObject linkingJson = newsLinkerFuture.result();
        JsonArray linkingValues = linkingJson.getJsonArray("value");

        JsonObject resultJson = new JsonObject().mergeIn(analysisJson);
        JsonArray resultJsonValues = resultJson.getJsonArray("value");

        // Iterate each value, and merge in the matching values from linkingJson
        resultJsonValues.stream()
                .map(article -> (JsonObject) article)
                .forEach(article -> linkingValues.stream()
                        .map(linkingArticle -> (JsonObject) linkingArticle)
                        .filter(linkingArticle -> linkingArticle.getString("url").equals(article.getString("url")))
                        .forEach(article::mergeIn));

        return resultJson;
    }

    private Future<StorageService> getStorageService() {
        final Future<StorageService> storageServiceFuture = Future.future();
        EventBusService.getProxy(serviceDiscovery, StorageService.class, storageServiceFuture.completer());

        return storageServiceFuture;
    }
}
