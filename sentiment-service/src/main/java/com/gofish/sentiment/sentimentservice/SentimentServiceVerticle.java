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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        // Once we know this verticles service has been announced to the cluster and our job queue is up and running,
        // we can go ahead and deploy the workers that monitor our queues and the timed crawler which will start
        // generating jobs and putting them into the queues
        CompositeFuture.all(publishFuture, jobQueueFuture).setHandler(resultHandler -> {
            if (resultHandler.succeeded()) {
                deployQueueWorkers().compose(v -> deployTimedCrawler()).setHandler(startFuture.completer());
                //deployQueueWorkers().setHandler(startFuture.completer());
            }
            else {
                LOG.error(resultHandler.cause());
                startFuture.fail(resultHandler.cause());
            }
        });
    }

    private Future<Void> deployQueueWorkers() {
        // Deploy worker verticles which poll each pending job queue
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config).setWorker(true).setInstances(2);
        List<Future> deployFutures = new ArrayList<>();

        Arrays.asList(
                NewsCrawlerJobMonitor.class.getName(),
                NewsLinkerJobMonitor.class.getName(),
                NewsAnalyserJobMonitor.class.getName()
        ).forEach(verticleWorker -> {
            Future<String> deployFuture = Future.future();
            deployFutures.add(deployFuture);
            vertx.deployVerticle(verticleWorker, deploymentOptions, deployFuture.completer());
        });

        return CompositeFuture.all(deployFutures).map(v -> null);
    }

    private Future<Void> deployTimedCrawler() {
        Future<String> deployFuture = Future.future();
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config).setWorker(true);

        vertx.deployVerticle(PeriodicCrawlerWorker.class.getName(), deploymentOptions, deployFuture.completer());

        return deployFuture.map(v -> null);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        Future<Void> recordUnpublishFuture = Future.future();
        Future<Void> messageConsumerUnregisterFuture = Future.future();

        serviceDiscovery.unpublish(record.getRegistration(), recordUnpublishFuture.completer());
        messageConsumer.unregister(messageConsumerUnregisterFuture.completer());

        recordUnpublishFuture.compose(v -> {
            serviceDiscovery.close();
            return messageConsumerUnregisterFuture;
        }).setHandler(stopFuture.completer());
    }
}
