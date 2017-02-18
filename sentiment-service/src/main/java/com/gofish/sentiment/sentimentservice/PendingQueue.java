package com.gofish.sentiment.sentimentservice;

/**
 * @author Luke Herron
 */
public enum PendingQueue {

    NEWS_CRAWLER("newsCrawler:pendingQueue"),
    NEWS_ANALYSER("newsAnalyser:pendingQueue"),
    NEWS_LINKER("newsLinker:pendingQueue");

    private final String queueName;

    PendingQueue(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public String toString() {
        return queueName;
    }
}
