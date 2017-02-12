package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.sentimentservice.monitor.NewsAnalyserJobMonitor;
import com.gofish.sentiment.sentimentservice.monitor.NewsCrawlerJobMonitor;
import com.gofish.sentiment.sentimentservice.monitor.NewsLinkerJobMonitor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author Luke Herron
 */
public class SentimentServiceVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(SentimentServiceVerticle.class);

    private JsonObject config;
    private SentimentService sentimentService;
    private MessageConsumer<JsonObject> messageConsumer;
    private ServiceDiscovery serviceDiscovery;
    private Record record;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOG.info("Bringing up SentimentService verticle");

        this.config = Optional.ofNullable(config())
                .orElseThrow(() -> new RuntimeException("Could not load sentiment service configuration"));

        Future<Record> publishFuture = Future.future();
        Future<String> jobQueueFuture = Future.future();

        sentimentService = SentimentService.create(vertx, config);
        messageConsumer = ProxyHelper.registerService(SentimentService.class, vertx, sentimentService, SentimentService.ADDRESS);
        serviceDiscovery = ServiceDiscovery.create(vertx);
        record = EventBusService.createRecord(SentimentService.NAME, SentimentService.ADDRESS, SentimentService.class);

        serviceDiscovery.publish(record, publishFuture.completer());

        // NewsCrawler workers immediately start polling the redis job queue on launch, so ensure that the redis service
        // is actually up and running (and responds to a ping) before launching the workers.
        RedisClient.create(vertx, new RedisOptions().setHost("redis")).ping(jobQueueFuture.completer());

        CompositeFuture.all(publishFuture, jobQueueFuture).setHandler(resultHandler -> {
            if (resultHandler.succeeded()) {
                deployQueueWorkers();
                startFuture.complete();
            }
            else {
                LOG.error(resultHandler.cause());
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private void deployQueueWorkers() {
        // Deploy worker verticles which poll each pending job queue
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config).setWorker(true).setInstances(8);

        Arrays.asList(
                NewsCrawlerJobMonitor.class.getName(),
                NewsLinkerJobMonitor.class.getName(),
                NewsAnalyserJobMonitor.class.getName()
        ).forEach(verticleWorker ->
                vertx.deployVerticle(verticleWorker, deploymentOptions, completionHandler -> {
                    if (completionHandler.succeeded()) {
                        LOG.info("QueueWorker deployed: " + completionHandler.result());
                    }
                    else {
                        LOG.error(completionHandler.cause());
                    }
                })
        );
    }
}
