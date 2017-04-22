package com.gofish.sentiment.storage;

import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.ext.mongo.MongoClient;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.Record;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class StorageVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(StorageVerticle.class);

    private MongoClient mongo;
    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up StorageVerticle");

        JsonObject config = Optional.ofNullable(config()).orElseThrow(() -> new RuntimeException("Could not load storage service configuration"));

        // We bring up the mongo client here, even though it is utilised in the worker verticles, and not here.
        // This is to enable us to keep a connection open, and simply close it when this verticle closes, rather than
        // closing it everytime the worker verticle stops (which is after every DB call).
        mongo = MongoClient.createShared(vertx, config);

        // Initialise a service proxy and publish it for service discovery
        com.gofish.sentiment.storage.rxjava.StorageService storageService = com.gofish.sentiment.storage.rxjava.StorageService.create(vertx, config());
        messageConsumer = ProxyHelper.registerService(StorageService.class, vertx.getDelegate(), storageService.getDelegate(), StorageService.ADDRESS);

        serviceDiscovery = ServiceDiscovery.create(vertx, serviceDiscovery -> {
            LOG.info("Service Discovery intialised");
            record = EventBusService.createRecord(StorageService.NAME, StorageService.ADDRESS, StorageService.class.getName());

            serviceDiscovery.rxPublish(record).subscribe(r -> startFuture.complete(), startFuture::fail);
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        ObservableFuture<Void> messageConsumerObservable = new ObservableFuture<>();
        serviceDiscovery.rxUnpublish(record.getRegistration())
                .flatMapObservable(v -> {
                    messageConsumer.unregister(messageConsumerObservable.toHandler());
                    return messageConsumerObservable;
                })
                .doOnNext(v -> { serviceDiscovery.close(); mongo.close(); })
                .subscribe(RxHelper.toSubscriber(stopFuture));
    }
}
