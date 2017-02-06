package com.gofish.sentiment.newsanalyser;

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
public class NewsAnalyserVerticleTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private NewsAnalyserVerticle newsAnalyserVerticle;

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();
        newsAnalyserVerticle = new NewsAnalyserVerticle();
    }

    @Test
    public void testStartingVerticle(TestContext context) {
        vertx.deployVerticle(newsAnalyserVerticle, context.asyncAssertSuccess(context::assertNotNull));
    }

    @Test
    public void testStoppingVerticle(TestContext context) {
        vertx.deployVerticle(newsAnalyserVerticle, context.asyncAssertSuccess(result -> {
            vertx.close(context.asyncAssertSuccess());
        }));
    }
}
