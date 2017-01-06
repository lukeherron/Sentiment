package com.gofish.sentiment.verticle;


import io.vertx.core.DeploymentOptions;
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
        deployCrawler();
        vertx.periodicStream(CrawlerVerticle.TIMER_DELAY).toObservable().subscribe(id -> deployCrawler());
    }

    private void deployCrawler() {
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config());
        RxHelper.deployVerticle(vertx, new CrawlerVerticle(), deploymentOptions).subscribe(
                deploymentId -> vertx.undeploy(deploymentId),
                failure -> logger.error(failure.getMessage(), failure.getCause()),
                () -> logger.info("Crawler deployment complete")
        );
    }
}
