package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.sentimentservice.job.CrawlerJob;
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
        final Future<JsonObject> newsAnalyserFuture = Future.future();
        final Future<JsonObject> newsLinkerFuture = Future.future();
        final CompositeFuture analyseSentimentFuture = CompositeFuture.join(newsAnalyserFuture, newsLinkerFuture);

        final CrawlerJob job = new CrawlerJob(query);

        // Set up a unique listener to receive the results of a processed job. There are three specific job queues
        // (although each are split into pending/working), but we only need to listen for two: news-analyser and
        // news-linker, as both of these queues work on the results of the news-crawler job queue, i.e. we know we can't
        // receive the final result from the news-crawler, so we exclude listening for its results.
        final MessageConsumer<JsonObject> analyseConsumer = vertx.eventBus().localConsumer("news-analyser:" + job.getJobId(), messageHandler -> {
            final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            newsAnalyserFuture.complete(result);
        });

        final MessageConsumer<JsonObject> linkerConsumer = vertx.eventBus().localConsumer("news-linker:" + job.getJobId(), messageHandler -> {
            final JsonObject result = Optional.ofNullable(messageHandler.body()).orElseGet(JsonObject::new);
            newsLinkerFuture.complete(result);
        });

        analyseConsumer.exceptionHandler(newsAnalyserFuture::fail);
        linkerConsumer.exceptionHandler(newsLinkerFuture::fail);
        analyseConsumer.endHandler(endHandler -> analyseConsumer.unregister());
        linkerConsumer.endHandler(endHandler -> linkerConsumer.unregister());

        // With the listeners ready, we can push the job onto the queue
        redis.lpush(PendingQueue.NEWS_CRAWLER.toString(), job.toJson().encode(), pushHandler -> {
            if (pushHandler.succeeded()) {
                LOG.info("Job added to queue: " + pushHandler.result());
            }
            else if (!analyseSentimentFuture.failed()) {
                analyseSentimentFuture.fail(pushHandler.cause());
            }
        });

        analyseSentimentFuture
                .map(future -> mergeFutureResults(newsAnalyserFuture, newsLinkerFuture))
                .compose(json -> saveAnalysis(query, json.getJsonArray("value")))
                .compose(json -> getStorageService().compose(storageService -> {
                    final Future<JsonObject> sentimentResultsFuture = Future.future();
                    storageService.getSentimentResults(query, sentimentResultsFuture.completer());
                    return sentimentResultsFuture;
                }))
                .setHandler(resultHandler);

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
                            .put("datePublished", 1)
                            .put("description", 1);

                    storageService.createIndex(query, collectionIndex, createIndexFuture.completer());
                    return createIndexFuture;
                })
                .setHandler(addNewQueryFuture.completer());

        return addNewQueryFuture;
    }

    private JsonObject mergeFutureResults(Future<JsonObject> newsAnalyserFuture, Future<JsonObject> newsLinkerFuture) {
        final JsonObject analysisJson = newsAnalyserFuture.result().getJsonObject("sentimentResponse");
        final JsonObject linkingJson = newsLinkerFuture.result().getJsonObject("entityLinkingResponse");
        final JsonArray linkingValues = linkingJson.getJsonArray("value");

        final JsonObject resultJson = new JsonObject().mergeIn(analysisJson);
        final JsonArray resultJsonValues = resultJson.getJsonArray("value");

        // Iterate each value, and merge in the matching values from linkingJson
        resultJsonValues.stream()
                .map(article -> (JsonObject) article)
                .forEach(article -> linkingValues.stream()
                        .map(linkingArticle -> (JsonObject) linkingArticle)
                        .filter(linkingArticle -> linkingArticle.getString("url").equals(article.getString("url")))
                        .forEach(article::mergeIn));

        return resultJson;
    }

    private Future<JsonObject> saveAnalysis(String query, JsonArray articles) {
        final Future<JsonObject> saveAnalysisFuture = Future.future();

        return getStorageService().compose(storageService -> {
            storageService.saveArticles(query, articles, saveAnalysisFuture.completer());
            return saveAnalysisFuture;
        });
    }

    private Future<StorageService> getStorageService() {
        final Future<StorageService> storageServiceFuture = Future.future();
        EventBusService.getProxy(serviceDiscovery, StorageService.class, storageServiceFuture.completer());

        return storageServiceFuture;
    }
}
