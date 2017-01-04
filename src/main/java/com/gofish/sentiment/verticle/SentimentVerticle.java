package com.gofish.sentiment.verticle;

import com.gofish.sentiment.service.CrawlerService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.RxHelper;

/**
 * @author Luke Herron
 */
public class SentimentVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SentimentVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        RxHelper.deployVerticle(vertx, new MongoVerticle())
                .doOnNext(id -> deployCrawler())
                .subscribe(this::deployTimedCrawler, startFuture::fail, startFuture::complete);
    }

    private void deployCrawler() {
        RxHelper.deployVerticle(vertx, new CrawlerVerticle()).subscribe(
                this::startCrawl,
                failure -> logger.error(failure.getMessage(), failure.getCause()),
                () -> logger.info("Crawler Verticle deployed")
        );
    }

    private void deployTimedCrawler(String deploymentId) {
        vertx.periodicStream(15000).toObservable().subscribe(
                result -> deployCrawler(),
                failure -> logger.error(failure.getMessage(), failure.getCause()),
                () -> vertx.undeploy(deploymentId));
    }

    private void startCrawl(String deploymentId) {
        ObservableFuture<JsonArray> crawlerResponseFuture = io.vertx.rx.java.RxHelper.observableFuture();

        CrawlerService crawlerService = CrawlerService.createProxy(getVertx(), CrawlerVerticle.ADDRESS);
        crawlerService.startCrawl(crawlerResponseFuture.toHandler());

        crawlerResponseFuture.subscribe(
                result -> logger.info(result.encodePrettily()),
                failure -> logger.error(failure.getMessage(), failure.getCause()),
                () -> vertx.undeploy(deploymentId));
    }
}
