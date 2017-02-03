package com.gofish.sentiment.storage;

import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Luke Herron
 *
 * The tests represented here are largely undertaken for learning. Practically all of the methods implemented in the
 * StorageService interface implementation call 'vertx.deployVerticle()' and 'vertx.eventBus()' both of which have been
 * mocked (and yes, both of which we don't own...). The integration tests for this module remain unmocked and stands as
 * the practical test for the StorageService interface and implementation class.
 */
@RunWith(VertxUnitRunner.class)
public class StorageServiceTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private StorageService storageService;

    @Before
    public void setUp() {
        vertx = mock(VertxImpl.class);
        storageService = StorageService.create(vertx, new JsonObject());

        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateCollectionSucceeds(TestContext context) {
        prepareSuccessScenario(mock(Message.class));

        storageService.createCollection("testCollection", context.asyncAssertSuccess());
    }

    private void prepareSuccessScenario(Message<Object> message) {
        Future<Message<Object>> future = Future.succeededFuture(message);
        setMessageResponse(future);
        setDeployVerticleSucceeds();
    }

    private void prepareFailureScenario() {
        Future<Message<Object>> future = Future.failedFuture("failed test");
        setMessageResponse(future);
        setDeployVerticleSucceeds();
    }

    private void setDeployVerticleSucceeds() {
        Future<String> deployFuture = Future.succeededFuture("test.id");
        setDeployVerticleResponse(deployFuture);
    }

    private void setDeployVerticleResponse(Future<String> future) {
        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(2);
            handler.handle(future);
            return null;
        }).when(vertx).deployVerticle(anyString(), any(DeploymentOptions.class), any());
    }

    private void setMessageResponse(Future<Message<Object>> future) {
        when(vertx.eventBus().send(anyString(), any(JsonObject.class), any(DeliveryOptions.class), any())).thenAnswer(invocation -> {
            Handler<AsyncResult<Message<Object>>> handler = invocation.getArgument(3);
            handler.handle(future);
            return null;
        });
    }
}
