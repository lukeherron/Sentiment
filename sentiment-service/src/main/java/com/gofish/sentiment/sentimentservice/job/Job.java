package com.gofish.sentiment.sentimentservice.job;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author Luke Herron
 */
public interface Job {

    @VertxGen
    enum State { INACTIVE, ACTIVE, COMPLETE, FAILED, DELAYED }
}
