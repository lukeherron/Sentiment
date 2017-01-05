package com.gofish.sentiment.verticle;


import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.RxHelper;

/**
 * @author Luke Herron
 */
public class SentimentVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SentimentVerticle.class);

    @Override
    public void start() throws Exception {
        CrawlerVerticle crawlerVerticle = new CrawlerVerticle();
        RxHelper.deployVerticle(vertx, crawlerVerticle).subscribe(
                deploymentId -> logger.debug("Deploying Crawler: " + deploymentId),
                failure -> logger.error(failure.getMessage(), failure.getCause()),
                () -> vertx.undeploy(crawlerVerticle.deploymentID())
        );

        vertx.periodicStream(CrawlerVerticle.TIMER_DELAY).toObservable().subscribe(id ->
                RxHelper.deployVerticle(vertx, crawlerVerticle).subscribe(
                        deploymentId -> logger.debug("Deploying Crawler: " + deploymentId),
                        failure -> logger.error(failure.getMessage(), failure.getCause()),
                        () -> vertx.undeploy(crawlerVerticle.deploymentID())
                )
        );
    }
}
