package com.gofish.sentiment.storage;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class StorageVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(StorageVerticle.class);

    private JsonObject config;
    private MongoClient mongo;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up StorageVerticle");

        this.config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load storage service configuration"));

        // We bring up the mongo client here, even though it is utilised in the worker verticles, and not here.
        // This is to enable us to keep a connection open, and simply close it when this verticle closes, rather than
        // closing it everytime the worker verticle stops (which is after every DB call).
        mongo = MongoClient.createShared(vertx, config());

        // TODO: Initialise a service proxy and publish it for service discovery
    }

    public void stop(Future<Void> stopFuture) throws Exception {
        mongo.close();
    }
}
