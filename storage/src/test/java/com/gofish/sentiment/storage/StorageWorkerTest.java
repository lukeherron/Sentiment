package com.gofish.sentiment.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.ext.mongo.MongoClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import rx.Observable;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class StorageWorkerTest {

    private Vertx vertx;
    private MongoClient mongo;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        mongo = mock(MongoClient.class);

        StorageWorker storageWorker = new StorageWorker(mongo);
        vertx.deployVerticle(storageWorker, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateCollectionRepliesSuccessWithValidCollectionName(TestContext context) {
        // We want to return a null observable for onnext, without getting hacky with rxjava (i.e. Observable.just(null)
        // on the off-chance that vertx updates to rxjava 2, which doesn't allow null values), so we'll just utilise a
        // void future and RxHelper's handy observableFuture
        ObservableFuture<Void> createCollectionFuture = RxHelper.observableFuture();
        createCollectionFuture.toHandler().handle(Future.succeededFuture());

        when(mongo.createCollectionObservable(anyString())).thenReturn(createCollectionFuture);

        when(mongo.getCollectionsObservable())
                .thenReturn(Observable.just(Collections.singletonList("notTheCollectionYouAreLookingFor")));

        JsonObject message = new JsonObject().put("collectionName", "testCollection");
        DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "createCollection");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateCollectionFailsIfCollectionAlreadyExists(TestContext context) {
        ObservableFuture<Void> createCollectionFuture = RxHelper.observableFuture();
        createCollectionFuture.toHandler().handle(Future.succeededFuture());

        when(mongo.createCollectionObservable(anyString())).thenReturn(createCollectionFuture);

        when(mongo.getCollectionsObservable())
                .thenReturn(Observable.just(Collections.singletonList("testCollection")));

        JsonObject message = new JsonObject().put("collectionName", "testCollection");
        DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "createCollection");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertFailure());
    }

    @Test
    public void testCreateIndexRepliesSuccessWithValidIndexDetails(TestContext context) {
        ObservableFuture<Void> createIndexFuture = RxHelper.observableFuture();
        createIndexFuture.toHandler().handle(Future.succeededFuture());

        final JsonObject collectionIndex = new JsonObject()
                .put("name", 1)
                .put("datePublished", 1)
                .put("description", 1);

        when(mongo.createIndexWithOptionsObservable(anyString(), any(JsonObject.class), any(IndexOptions.class)))
                .thenReturn(createIndexFuture);

        when(mongo.listIndexesObservable(anyString())).thenReturn(Observable.just(new JsonArray()));

        JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("indexName", "testCollectionIndex")
                .put("collectionIndex", collectionIndex);

        DeliveryOptions deliverOptions = new DeliveryOptions().addHeader("action", "createIndex");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliverOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateIndexFailsWhenIndexAlreadyExists(TestContext context) {
        ObservableFuture<Void> createIndexFuture = RxHelper.observableFuture();
        createIndexFuture.toHandler().handle(Future.succeededFuture());

        final JsonObject testCollectionIndex = new JsonObject()
                .put("name", 1)
                .put("datePublished", 1)
                .put("description", 1);

        final JsonObject testCollectionIndexMongoOutput = new JsonObject()
            .put("v", 2)
            .put("unique", true)
            .put("key", new JsonObject()
                    .put("name", 1)
                    .put("datePublished", 1)
                    .put("description", 1))
            .put("name", "testCollectionIndex")
            .put("ns", "DEFAULT_DB.testCollection");

        when(mongo.createIndexWithOptionsObservable(anyString(), any(JsonObject.class), any(IndexOptions.class)))
                .thenReturn(createIndexFuture);

        when(mongo.listIndexesObservable(anyString()))
                .thenReturn(Observable.just(new JsonArray().add(testCollectionIndexMongoOutput)));

        JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("indexName", "testCollectionIndex")
                .put("collectionIndex", testCollectionIndex);

        DeliveryOptions deliverOptions = new DeliveryOptions().addHeader("action", "createIndex");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliverOptions, context.asyncAssertFailure());
    }
}
