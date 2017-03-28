package com.gofish.sentiment.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.ext.mongo.MongoClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import rx.Single;

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

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private MongoClient mongo;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();
        mongo = mock(MongoClient.class);

        StorageWorker storageWorker = new StorageWorker(mongo);

        vertx.deployVerticle(storageWorker, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateCollectionRepliesSuccessWithValidCollectionName(TestContext context) {
        // We want to return a null observable for onnext, without getting hacky with rxjava (i.e. Observable.just(null)
        // on the off-chance that vertx updates to rxjava 2, which doesn't allow null values), so we'll just utilise a
        // void future and RxHelper's handy observableFuture
        final ObservableFuture<Void> createCollectionFuture = RxHelper.observableFuture();
        createCollectionFuture.toHandler().handle(Future.succeededFuture());

        when(mongo.rxCreateCollection(anyString())).thenReturn(createCollectionFuture.toSingle());

        when(mongo.rxGetCollections())
                .thenReturn(Single.just(Collections.singletonList("notTheCollectionYouAreLookingFor")));

        final JsonObject message = new JsonObject().put("collectionName", "testCollection");
        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "createCollection");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateCollectionFailsIfCollectionAlreadyExists(TestContext context) {
        final ObservableFuture<Void> createCollectionFuture = RxHelper.observableFuture();
        createCollectionFuture.toHandler().handle(Future.succeededFuture());

        when(mongo.rxCreateCollection(anyString())).thenReturn(createCollectionFuture.toSingle());

        when(mongo.rxGetCollections())
                .thenReturn(Single.just(Collections.singletonList("testCollection")));

        final JsonObject message = new JsonObject().put("collectionName", "testCollection");
        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "createCollection");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertFailure());
    }

    @Test
    public void testCreateIndexRepliesSuccessWithValidIndexDetails(TestContext context) {
        final ObservableFuture<Void> createIndexFuture = RxHelper.observableFuture();
        createIndexFuture.toHandler().handle(Future.succeededFuture());

        final JsonObject collectionIndex = new JsonObject()
                .put("name", 1)
                .put("datePublished", 1)
                .put("description", 1);

        when(mongo.rxCreateIndexWithOptions(anyString(), any(JsonObject.class), any(IndexOptions.class)))
                .thenReturn(createIndexFuture.toSingle());

        when(mongo.rxListIndexes(anyString())).thenReturn(Single.just(new JsonArray()));

        final JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("indexName", "testCollectionIndex")
                .put("collectionIndex", collectionIndex);

        final DeliveryOptions deliverOptions = new DeliveryOptions().addHeader("action", "createIndex");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliverOptions, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateIndexFailsWhenIndexAlreadyExists(TestContext context) {
        final ObservableFuture<Void> createIndexFuture = RxHelper.observableFuture();
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

        when(mongo.rxCreateIndexWithOptions(anyString(), any(JsonObject.class), any(IndexOptions.class)))
                .thenReturn(createIndexFuture.toSingle());

        when(mongo.rxListIndexes(anyString()))
                .thenReturn(Single.just(new JsonArray().add(testCollectionIndexMongoOutput)));

        final JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("indexName", "testCollectionIndex")
                .put("collectionIndex", testCollectionIndex);

        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "createIndex");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertFailure());
    }

    @Test
    public void testGetCollectionsRepliesWithJsonArray(TestContext context) {
        when(mongo.rxGetCollections()).thenReturn(Single.just(Collections.singletonList("testCollection")));

        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "getCollections");

        vertx.eventBus().send(StorageWorker.ADDRESS, new JsonObject(), deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonArray collections = (JsonArray) result.body();
            context.assertTrue(collections.size() > 0);
            context.assertEquals(collections.getString(0), "testCollection");
        }));
    }

    @Test
    public void testGetSentimentResultsRepliesWithJsonObject(TestContext context) {
        when(mongo.rxRunCommand(anyString(), any(JsonObject.class))).thenReturn(Single.just(new JsonObject()));

        final JsonObject message = new JsonObject().put("collectionName", "testCollection");
        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "getSentimentResults");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonObject results = (JsonObject) result.body();
            context.assertTrue(results.size() == 0);
        }));
    }

    @Test
    public void testHasCollectionRepliesTrueIfCollectionExists(TestContext context) {
        when(mongo.rxGetCollections()).thenReturn(Single.just(Collections.singletonList("testCollection")));

        final JsonObject message = new JsonObject().put("collectionName", "testCollection");
        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "hasCollection");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            boolean hasCollection = (boolean) result.body();
            context.assertTrue(hasCollection);
        }));
    }

    @Test
    public void testHasCollectionRepliesFalseIfCollectionDoesNotExist(TestContext context) {
        when(mongo.rxGetCollections()).thenReturn(Single.just(Collections.singletonList("notTheCollectionYouAreLookingFor")));

        final JsonObject message = new JsonObject().put("collectionName", "testCollection");
        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "hasCollection");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            boolean hasCollection = (boolean) result.body();
            context.assertFalse(hasCollection);
        }));
    }

    @Test
    public void testIsIndexPresentRepliesTrueIfIndexExists(TestContext context) {
        final JsonObject testCollectionIndexMongoOutput = new JsonObject()
                .put("v", 2)
                .put("unique", true)
                .put("key", new JsonObject()
                        .put("name", 1)
                        .put("datePublished", 1)
                        .put("description", 1))
                .put("name", "testCollectionIndex")
                .put("ns", "DEFAULT_DB.testCollection");

        when(mongo.rxListIndexes(anyString()))
                .thenReturn(Single.just(new JsonArray().add(testCollectionIndexMongoOutput)));

        final JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("indexName", "testCollectionIndex");

        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "isIndexPresent");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            boolean isIndexPresent = (boolean) result.body();
            context.assertTrue(isIndexPresent);
        }));
    }

    @Test
    public void testIsIndexPresentRepliesFalseIfIndexDoesNotExist(TestContext context) {
        final JsonObject testCollectionIndexMongoOutput = new JsonObject()
                .put("v", 2)
                .put("unique", true)
                .put("key", new JsonObject()
                        .put("name", 1)
                        .put("datePublished", 1)
                        .put("description", 1))
                .put("name", "notTheCollectionYouAreLookingForIndex")
                .put("ns", "DEFAULT_DB.notTheCollectionYouAreLookingFor");

        when(mongo.rxListIndexes(anyString()))
                .thenReturn(Single.just(new JsonArray().add(testCollectionIndexMongoOutput)));

        final JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("indexName", "testCollectionIndex");

        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "isIndexPresent");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            boolean isIndexPresent = (boolean) result.body();
            context.assertFalse(isIndexPresent);
        }));
    }

    @Test
    public void testSaveArticlesRepliesSuccessWithJsonObject(TestContext context) {
        when(mongo.rxRunCommand(anyString(), any(JsonObject.class)))
                .thenReturn(Single.just(new JsonObject().put("status", "success")));

        final JsonObject message = new JsonObject()
                .put("collectionName", "testCollection")
                .put("articles", new JsonArray());

        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "saveArticles");

        vertx.eventBus().send(StorageWorker.ADDRESS, message, deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            JsonObject reply = (JsonObject) result.body();
            context.assertEquals("success", reply.getString("status"));
        }));
    }

    @Test
    public void testMessageHandlerNotifiesOfInvalidAction(TestContext context) {
        final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "actionThatDoesNotExist");

        vertx.eventBus().send(StorageWorker.ADDRESS, new JsonObject(), deliveryOptions, context.asyncAssertSuccess(result -> {
            context.assertNotNull(result.body());
            String reply = result.body().toString();
            context.assertEquals("Invalid Action", reply);
        }));
    }
}
