package com.gofish.sentiment.storage;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class StorageVerticleIT {

    private Vertx vertx;
    private ServiceDiscovery serviceDiscovery;
    private StorageService storageService;

    private String serviceDiscoveryStatus = "";

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.exceptionHandler(context.exceptionHandler());
        serviceDiscovery = ServiceDiscovery.create(vertx);

        vertx.eventBus().<JsonObject>consumer("vertx.discovery.announce", messageHandler -> {
            JsonObject announce = messageHandler.body();
            serviceDiscoveryStatus = announce.getString("status");
        });

        vertx.deployVerticle(StorageVerticle.class.getName(), context.asyncAssertSuccess(result -> {
            EventBusService.getProxy(serviceDiscovery, StorageService.class, context.asyncAssertSuccess(storageService -> {
                this.storageService = storageService;
            }));
        }));
    }

    @Test
    public void testStorageServicePublishedStatus(TestContext context) {
        context.assertEquals(serviceDiscoveryStatus, "UP", "Service Discovery Status returned '" +
                serviceDiscoveryStatus + "'. Expected 'UP'");
    }

    @Test
    public void testStorageServiceUnpublishedStatus(TestContext context) {
        vertx.close(completionHandler -> {
            context.assertEquals(serviceDiscoveryStatus, "DOWN", "Service Discovery Status returned '" +
                    serviceDiscoveryStatus + "'. Expected 'DOWN'");
        });
    }

    @Test
    public void testPublishedStorageServiceNotNull(TestContext context) {
        context.assertNotNull(storageService);
    }

    @Test
    public void testStorageServiceProxyNotNull(TestContext context) {
        StorageService service = StorageService.createProxy(vertx, StorageService.ADDRESS);
        context.assertNotNull(service);
    }
}
