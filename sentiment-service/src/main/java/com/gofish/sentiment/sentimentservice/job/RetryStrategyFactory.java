package com.gofish.sentiment.sentimentservice.job;

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

/**
 * @author Luke Herron
 */
public class RetryStrategyFactory {

    private static final int DEFAULT_RETRY_DELAY_SECONDS = 2;
    private static final Logger LOG = LoggerFactory.getLogger(RetryStrategyFactory.class);

    public static void calculate(Job job, Throwable throwable) {
        if (throwable instanceof ReplyException) {
            calculateFromReplyException(job, (ReplyException) throwable);
        }
        else {
            LOG.error("Encountered unhandled exception: " + throwable.getClass(), throwable);
        }
    }

    private static void calculateFromReplyException(Job job, ReplyException replyException) {
        switch (replyException.failureType()) {
            case NO_HANDLERS:
            case TIMEOUT:
                // No retry strategy required. Set the default retry (with fallback) and requeue.
                job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
                break;
            case RECIPIENT_FAILURE:
                JsonObject errorMessage = new JsonObject(replyException.getMessage());
                processErrorMessage(job, errorMessage);
                break;
            default:
                throw new IllegalStateException("Invalid FailureType argument");
        }
    }

    private static void processErrorMessage(Job job, JsonObject errorMessage) {
        JsonObject errorJson = errorMessage.getJsonObject("error", new JsonObject());
        Integer statusCode = Optional.ofNullable(errorJson.getInteger("statusCode"))
                .orElseGet(() -> errorMessage.getInteger("statusCode", 0));

        String message = Optional.ofNullable(errorJson.getString("message"))
                .orElseGet(() -> errorMessage.getString("message"));

        switch (statusCode) {
            case 429:
                // Too many attempts. Indicate wait is required
                try {
                    int delay = Integer.parseInt(message.replaceAll("[^\\d]", ""));
                    job.setRetryStrategy(new RetryStrategy(delay));
                }
                catch (NumberFormatException e) {
                    LOG.error("Could not extract wait time from error message:\n"
                            + errorMessage.encodePrettily(), e);

                    job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
                }
                break;
            case 0:
                // Method parameter errorMessage is not a valid json object
                LOG.error("Encountered invalid error message in RetryStrategy#processErrorMessage()");
                LOG.error(errorMessage.encodePrettily());
                job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
                break;
            default:
                LOG.error("Encountered unhandled statusCode (RetryStrategy#processErrorMessage()) :" + statusCode);
                job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
        }
    }
}
