package com.gofish.sentiment.storage;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class StorageVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(StorageVerticle.class);

    private JsonObject config;
    private MongoClient mongo;
    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up StorageVerticle");

        this.config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load storage service configuration"));

        // We bring up the mongo client here, even though it is utilised in the worker verticles, and not here.
        // This is to enable us to keep a connection open, and simply close it when this verticle closes, rather than
        // closing it everytime the worker verticle stops (which is after every DB call).
        mongo = MongoClient.createShared(vertx, config());

        // Initialise a service proxy and publish it for service discovery
        StorageService storageService = StorageService.create(vertx, config());
        messageConsumer = ProxyHelper.registerService(StorageService.class, vertx, storageService, StorageService.ADDRESS);
        serviceDiscovery = ServiceDiscovery.create(vertx);
        record = EventBusService.createRecord(StorageService.NAME, StorageService.ADDRESS, StorageService.class);

        serviceDiscovery.publish(record, resultHandler -> {
            if (resultHandler.succeeded()) {
                startFuture.complete();
            }
            else {
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    public void stop(Future<Void> stopFuture) throws Exception {
        Future<Void> recordUnpublishFuture = Future.future();
        Future<Void> messageConsumerUnregisterFuture = Future.future();

        serviceDiscovery.unpublish(record.getRegistration(), recordUnpublishFuture.completer());
        messageConsumer.unregister(messageConsumerUnregisterFuture.completer());

        recordUnpublishFuture.compose(v -> messageConsumerUnregisterFuture).setHandler(v -> {
            serviceDiscovery.close();
            mongo.close();

            if (v.succeeded()) {
                stopFuture.complete();
            }
            else {
                stopFuture.failed();
            }
        });
    }
}
