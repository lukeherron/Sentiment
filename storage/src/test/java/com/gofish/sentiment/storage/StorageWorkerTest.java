package com.gofish.sentiment.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
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
    public void testCollectionIsCreatedSuccessfully(TestContext context) {
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
}
