package com.gofish.sentiment.sentimentservice;

/**
 * @author Luke Herron
 */
public enum WorkingQueue {
    NEWS_CRAWLER("newsCrawler:workingQueue"),
    NEWS_ANALYSER("newsAnalyser:workingQueue"),
    NEWS_LINKER("newLinker:workingQueue");

    private final String queueName;

    WorkingQueue(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public String toString() {
        return queueName;
    }
}
