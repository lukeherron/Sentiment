package com.gofish.sentiment.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.servicediscovery.ServiceDiscovery;

/**
 * @author Luke Herron
 */
public class APIGatewayVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(APIGatewayVerticle.class);

    private ServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        serviceDiscovery = ServiceDiscovery.create(vertx);

        startFuture.complete();
    }
}
