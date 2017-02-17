package com.gofish.sentiment.newslinker;

import io.vertx.core.*;
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
 */
@RunWith(VertxUnitRunner.class)
public class NewsLinkerServiceTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private NewsLinkerService newsLinkerService;

    @Before
    public void setUp() {
        vertx = mock(VertxImpl.class);
        newsLinkerService = NewsLinkerService.create(vertx, new JsonObject());

        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
    }

    @Test
    public void testNewsLinkerServiceProxyIsCreated(TestContext context) {
        NewsLinkerService newsLinkerService = NewsLinkerService.createProxy(vertx, NewsLinkerService.ADDRESS);

        context.assertNotNull(newsLinkerService);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLinkEntitiesSucceeds(TestContext context) {
        Future<String> deployFuture = Future.succeededFuture("test.id");

        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(2);
            handler.handle(deployFuture);
            return invocation.getMock();
        }).when(vertx).deployVerticle(anyString(), any(DeploymentOptions.class), any());

        Message<Object> message = mock(Message.class);
        Future<Message<Object>> future = Future.succeededFuture(message);

        when(vertx.eventBus().send(anyString(), any(JsonObject.class), any(Handler.class))).thenAnswer(invocation -> {
            Handler<AsyncResult<Message<Object>>> handler = invocation.getArgument(2);
            handler.handle(future);
            return invocation.getMock();
        });

        newsLinkerService.linkEntities(new JsonObject().put("name", "test article"), context.asyncAssertSuccess());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLinkEntitiesFails(TestContext context) {
        Future<String> deployFuture = Future.succeededFuture("test.id");

        doAnswer(invocation -> {
            Handler<AsyncResult<String>> handler = invocation.getArgument(2);
            handler.handle(deployFuture);
            return invocation.getMock();
        }).when(vertx).deployVerticle(anyString(), any(DeploymentOptions.class), any());

        Future<Message<Object>> future = Future.failedFuture("failed test");

        when(vertx.eventBus().send(anyString(), any(JsonObject.class), any(Handler.class))).thenAnswer(invocation -> {
            Handler<AsyncResult<Message<Object>>> handler = invocation.getArgument(2);
            handler.handle(future);
            return invocation.getMock();
        });

        newsLinkerService.linkEntities(new JsonObject().put("name", "test article"), context.asyncAssertFailure(result -> {
            context.assertEquals("failed test", result.getMessage());
        }));
    }
}
