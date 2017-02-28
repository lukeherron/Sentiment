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

    private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 5;
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
                if (job.getAttempts() > DEFAULT_MAX_RETRY_ATTEMPTS) {
                    JsonObject errorMessage = new JsonObject(replyException.getMessage());
                    processTooManyAttempts(job, errorMessage);
                }
                else {
                    job.setState(Job.State.DELAYED);
                    job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
                }
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
            case 400: // Bad request
                processError400(job, errorMessage, message);
                break;
            case 413: // Request entity is too large
                processError413(job, errorMessage, message);
                break;
            case 415: // Unsupported media type
                processError415(job, errorMessage, message);
                break;
            case 429: // Too many attempts. Delay required.
                processError429(job, errorMessage, message);
                break;
            case 0: // Method parameter 'errorMessage' is not a valid json object
                processInvalidError(job, errorMessage, "Encountered invalid error message in RetryStrategy#processErrorMessage()");
                break;
            default:
                processInvalidError(job, errorMessage, "Encountered unhandled statusCode in RetryStrategy#processErrorMessage():" + statusCode);
        }
    }

    private static void processError400(Job job, JsonObject errorMessage, String message) {
        if (job.getAttempts() > DEFAULT_MAX_RETRY_ATTEMPTS) {
            processTooManyAttempts(job, errorMessage);
        }
        else {
            job.setState(Job.State.DELAYED);
            job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
        }
    }

    private static void processError413(Job job, JsonObject errorMessage, String message) {
        job.setState(Job.State.FAILED);
        job.setResult(errorMessage);
    }

    private static void processError415(Job job, JsonObject errorMessage, String message) {
        job.setState(Job.State.FAILED);
        job.setResult(errorMessage);
    }

    private static void processError429(Job job, JsonObject errorMessage, String message) {
        try {
            int delay = Integer.parseInt(message.replaceAll("[^\\d]", ""));
            job.setRetryStrategy(new RetryStrategy(delay));
        }
        catch (NumberFormatException e) {
            LOG.error("Could not extract wait time from error message:\n"
                    + errorMessage.encodePrettily(), e);

            job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
        }
    }

    private static void processInvalidError(Job job, JsonObject errorMessage, String logMessage) {
        LOG.error(logMessage);
        LOG.error(errorMessage.encodePrettily());

        if (job.getAttempts() > DEFAULT_MAX_RETRY_ATTEMPTS) {
            processTooManyAttempts(job, errorMessage);
        }
        else {
            job.setState(Job.State.DELAYED);
            job.setRetryStrategy(new RetryStrategy((int) job.getTimeout()));
        }
    }

    private static void processTooManyAttempts(Job job, JsonObject errorMessage) {
        job.setState(Job.State.FAILED);
        job.setResult(errorMessage);
    }
}
