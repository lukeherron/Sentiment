package com.gofish.sentiment.newscrawler;

import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;
import io.vertx.rxjava.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.Record;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class NewsCrawlerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(NewsCrawlerVerticle.class);

    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up NewsCrawlerVerticle");

        JsonObject config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load crawler configuration"));

        com.gofish.sentiment.newscrawler.rxjava.NewsCrawlerService newsCrawlerService = com.gofish.sentiment.newscrawler.rxjava.NewsCrawlerService.create(vertx, config);
        messageConsumer = ProxyHelper.registerService(NewsCrawlerService.class, vertx.getDelegate(), newsCrawlerService.getDelegate(), NewsCrawlerService.ADDRESS);

        serviceDiscovery = ServiceDiscovery.create(vertx, serviceDiscovery -> {
            LOG.info("Service Discovery initialised");
            record = EventBusService.createRecord(NewsCrawlerService.NAME, NewsCrawlerService.ADDRESS, NewsCrawlerService.class.getName());

            serviceDiscovery.rxPublish(record)
                    .subscribe(r -> startFuture.complete(), startFuture::fail);
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
                .doOnNext(v -> serviceDiscovery.close())
                .subscribe(RxHelper.toSubscriber(stopFuture));
    }
}
