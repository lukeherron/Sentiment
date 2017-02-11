package com.gofish.sentiment.newscrawler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class NewsCrawlerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerVerticle.class);

    private JsonObject config;
    private NewsCrawlerService newsCrawlerService;
    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up NewsCrawlerVerticle");

        this.config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load crawler configuration"));

        newsCrawlerService = NewsCrawlerService.create(vertx, config);
        messageConsumer = ProxyHelper.registerService(NewsCrawlerService.class, vertx, newsCrawlerService, NewsCrawlerService.ADDRESS);

        serviceDiscovery = ServiceDiscovery.create(vertx);
        record = EventBusService.createRecord(NewsCrawlerService.NAME, NewsCrawlerService.ADDRESS, NewsCrawlerService.class);

        serviceDiscovery.publish(record, resultHandler -> {
            if (resultHandler.succeeded()) {
                LOG.info("Published news crawler service successfully");
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

            if (v.succeeded()) {
                stopFuture.complete();
            }
            else {
                stopFuture.failed();
            }
        });
    }
}
