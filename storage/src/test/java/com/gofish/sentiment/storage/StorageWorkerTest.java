package com.gofish.sentiment.storage;

import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class StorageWorkerTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private MongoClient mongo;
    private StorageVerticle storageVerticle;

    @Before
    public void setUp(TestContext context) {
        vertx = vertxRule.vertx();
        mongo = mock(MongoClient.class);
    }

    @Test
    public void testVerticleDeploysSuccessfully(TestContext context) {
        vertx.deployVerticle(StorageWorker.class.getName(), context.asyncAssertSuccess(context::assertNotNull));
    }
}
