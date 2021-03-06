package com.gofish.sentiment.newsanalyser;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class NewsAnalyserVerticleTest {

    @Rule
    public final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private DeploymentOptions deploymentOptions;

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();

        JsonObject config = new JsonObject().put("api", new JsonObject()
                .put("base.url", "localhost")
                .put("url.path", "/text/analytics/v2.0/sentiment")
                .put("port", 80)
                .put("worker.instances", 8));

        deploymentOptions = new DeploymentOptions().setConfig(config);
    }

    @Test
    public void testStartingVerticle(TestContext context) {
        vertx.deployVerticle(NewsAnalyserVerticle.class.getName(), deploymentOptions, context.asyncAssertSuccess(context::assertNotNull));
    }

    @Test
    public void testStoppingVerticle(TestContext context) {
        vertx.deployVerticle(NewsAnalyserVerticle.class.getName(), deploymentOptions, context.asyncAssertSuccess(result -> {
            vertx.close(context.asyncAssertSuccess());
        }));
    }

    @Test
    public void testNewsAnalyserServicePublishedStatus(TestContext context) {
        final AtomicReference<String> serviceDiscoveryStatus = new AtomicReference<>();

        vertx.eventBus().<JsonObject>consumer("vertx.discovery.announce", messageHandler -> {
            final JsonObject announce = messageHandler.body();
            serviceDiscoveryStatus.set(announce.getString("status"));
        });

        vertx.deployVerticle(NewsAnalyserVerticle.class.getName(), deploymentOptions, context.asyncAssertSuccess(result -> {
            context.assertEquals(serviceDiscoveryStatus.get(), "UP", "Service Discovery Status returned '" +
                    serviceDiscoveryStatus + "'. Expected 'UP'");
        }));
    }
}
