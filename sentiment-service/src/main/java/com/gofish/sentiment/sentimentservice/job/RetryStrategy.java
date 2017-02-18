package com.gofish.sentiment.sentimentservice.job;

/**
 * @author Luke Herron
 */
public class RetryStrategy {

    private final long timeStamp;
    private int retryDelay;

    public RetryStrategy(int retryDelay) {
        timeStamp = System.currentTimeMillis() / 1000L;
        this.retryDelay = retryDelay;
    }

    public long getTimestamp() {
        return timeStamp;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }
}
