package com.gofish.sentiment.storage;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class StorageVerticleTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private StorageVerticle storageVerticle;

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();
        storageVerticle = new StorageVerticle();
    }

    @Test
    public void testStartingVerticle(TestContext context) {
        vertx.deployVerticle(storageVerticle, context.asyncAssertSuccess(context::assertNotNull));
    }

    @Test
    public void testStoppingVerticle(TestContext context) {
        vertx.deployVerticle(storageVerticle, context.asyncAssertSuccess(result -> {
            vertx.close(context.asyncAssertSuccess());
        }));
    }
}
