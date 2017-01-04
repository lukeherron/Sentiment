package com.gofish.sentiment.service.impl;

import com.gofish.sentiment.service.SentimentService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author Luke Herron
 */
public class SentimentServiceImpl implements SentimentService {

    private static final Logger logger = LoggerFactory.getLogger(SentimentServiceImpl.class);

    public SentimentServiceImpl(Vertx vertx, JsonObject config) {

    }
}
