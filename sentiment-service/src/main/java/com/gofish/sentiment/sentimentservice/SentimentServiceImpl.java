package com.gofish.sentiment.sentimentservice;

import com.gofish.sentiment.newsanalyser.rxjava.NewsAnalyserService;
import com.gofish.sentiment.newscrawler.rxjava.NewsCrawlerService;
import com.gofish.sentiment.newslinker.rxjava.NewsLinkerService;
import com.gofish.sentiment.storage.rxjava.StorageService;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.RxHelper;
import io.vertx.rx.java.SingleOnSubscribeAdapter;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import rx.Observable;
import rx.Single;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Luke Herron
 */
public class SentimentServiceImpl implements SentimentService {

    private static final Logger LOG = LoggerFactory.getLogger(SentimentServiceImpl.class);

    private final Vertx vertx;
    private final JsonObject config;
    private final ServiceDiscovery serviceDiscovery;
    private final CircuitBreaker newsAnalyserBreaker;
    private final CircuitBreaker newsCrawlerBreaker;
    private final CircuitBreaker newsLinkerBreaker;

    public SentimentServiceImpl(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
        serviceDiscovery = ServiceDiscovery.create(vertx);

        CircuitBreakerOptions breakerOptions = new CircuitBreakerOptions().setMaxRetries(0).setMaxFailures(1).setTimeout(-1);
        newsAnalyserBreaker = CircuitBreaker.create("news-analyser-breaker", vertx, breakerOptions);
        newsCrawlerBreaker = CircuitBreaker.create("news-crawler-breaker", vertx, breakerOptions);
        newsLinkerBreaker = CircuitBreaker.create("news-linker-breaker", vertx, breakerOptions);

        initBreakerOpenHandlers();
    }

    /**
     * Initialises the Circuit Breakers for each service. The breakers are configured to open on the first error, and
     * must be closed manually. The manual close is performed inside the openHandler of each breaker, after waiting for
     * a pre-determined delay which is retrieved from the related service.
     */
    private void initBreakerOpenHandlers() {
        newsAnalyserBreaker.openHandler(handler -> {
            rxGetService(NewsAnalyserService.name(), NewsAnalyserService.class)
                    .flatMap(service -> service.rxGetTimeout().doOnEach(notification -> release(service))
                            .flatMap(timeout -> Single.just(null).delay(timeout < 1 ? 1 : timeout, TimeUnit.MILLISECONDS)))
                    .subscribe(
                            v -> newsAnalyserBreaker.reset(),
                            f -> LOG.error("NewsAnalyser Breaker open handler error", f));
        });

        newsCrawlerBreaker.openHandler(handler -> {
            rxGetService(NewsCrawlerService.name(), NewsCrawlerService.class)
                    .flatMap(service -> service.rxGetTimeout().doOnEach(notification -> release(service))
                            .flatMap(timeout -> Single.just(null).delay(timeout < 1 ? 1 : timeout, TimeUnit.MILLISECONDS)))
                    .subscribe(
                            v -> newsCrawlerBreaker.reset(),
                            f -> LOG.error("NewsCrawler Breaker open handler error", f));
        });


        newsLinkerBreaker.openHandler(handler -> {
            rxGetService(NewsLinkerService.name(), NewsLinkerService.class)
                    .flatMap(service -> service.rxGetTimeout().doOnEach(notification -> release(service))
                            .flatMap(timeout -> Single.just(null).delay(timeout < 1 ? 1 : timeout, TimeUnit.MILLISECONDS)))
                    .subscribe(
                            v -> newsLinkerBreaker.reset(),
                            f -> LOG.error("NewsLinker Breaker open handler error", f));
        });

    }

    @Override
    public SentimentService analyseSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        this.<JsonObject>rxExecuteCommand(newsCrawlerBreaker, command ->
                rxGetService(NewsCrawlerService.name(), NewsCrawlerService.class)
                        .flatMap(service -> service.rxCrawlQuery(query).doOnEach(n -> release(service)))
                        .subscribe(RxHelper.toSubscriber(command.completer())))
                .flatMap(crawlResult -> rxFilterExistingArticles(query, crawlResult))
                .flatMapObservable(filteredResult -> {
                    JsonArray articles = filteredResult.getJsonArray("value");
                    // The original crawlResult has a 'totalEstimatedMatches' entry. Add a new entry which shows the
                    // total after filtering
                    filteredResult.put("totalFilteredMatches", articles.size());

                    Observable<Object> observableArticles = Observable.from(articles);
                    Observable<Long> interval = Observable.interval(200, TimeUnit.MILLISECONDS);

                    // We'll be good citizens and rate-limit each of our API requests by zipping each of our articles
                    // with a small delay
                    return Observable.zip(observableArticles, interval, (observable, timer) -> observable)
                            .map(json -> (JsonObject) json)
                            .flatMapSingle(article ->
                                    Single.zip(rxAnalyseSentiment(article), rxLinkEntities(article), (analysis, entities) ->
                                            article.mergeIn(new JsonObject().mergeIn(analysis).mergeIn(entities))))
                            .flatMapSingle(article -> rxSaveAnalysedArticle(query, article))
                            .last()
                            .map(results -> filteredResult);
                })
                .subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    @Override
    public SentimentService getSentiment(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        rxGetService(StorageService.name(), StorageService.class)
                .flatMap(service -> service.rxGetSentimentResults(query)
                        .doOnEach(notification -> release(service)))
                .flatMap(sentimentResult -> {
                    // If the result is empty it will be because the query has not been added and analysed. We
                    // perform both of these steps if empty, other we return the non-empty results.
                    if (sentimentResult.isEmpty()) {
                        final JsonObject collectionIndex = new JsonObject()
                                .put("name", 1)
                                .put("description", 1);

                        return rxGetService(StorageService.name(), StorageService.class)
                                .flatMap(service -> service.rxCreateCollection(query)
                                        .flatMap(v -> service.rxCreateIndex(query, collectionIndex))
                                        .doOnEach(notification -> release(service)))
                                .flatMap(v -> Single.create(new SingleOnSubscribeAdapter<JsonObject>(fut ->
                                        this.analyseSentiment(query, fut)))
                                        .map(result -> new JsonObject().put("result", result)));
                    }
                    else {
                        return Single.just(sentimentResult);
                    }
                })
                .subscribe(RxHelper.toSubscriber(resultHandler));

        return this;
    }

    /**
     * Performs sentiment analysis on each of the articles contained within the crawlResult.
     * @param crawlResult JsonObject which contains the articles to perform analysis on
     * @return Single which emits the results of the sentiment analyisis
     */
    private Single<JsonObject> rxAnalyseSentiment(JsonObject crawlResult) {

        return rxGetService(NewsAnalyserService.name(), NewsAnalyserService.class)
                // We wrap the call inside a CircuitBreaker so that we can continually retry certain errors, knowing
                // that the underlying service won't actual get called until the circuit breaker closes again. This is
                // handy for 429 responses (API rate limits). We simply keep retrying knowing that the circuit breaker
                // is preventing unnecessary network calls
                .flatMap(service -> this.<JsonObject>rxExecuteCommand(newsAnalyserBreaker, command ->
                        service.rxAnalyseSentiment(crawlResult)
                                .doOnError(error -> handlerError(error, service::setTimeout))
                                .subscribe(RxHelper.toSubscriber(command.completer())))
                        .retryWhen(errors -> errors.flatMap(error -> {
                            // TODO: implement a more robust retry method
                            if (error.getMessage().contains("429") || error.getMessage().contains("open circuit")) {
                                return Observable.just(null).delay(200, TimeUnit.MILLISECONDS);
                            }

                            release(service);
                            return Observable.error(error);
                        })));
    }

    /**
     * Checks the articles returned from the crawl result to see if they already exist in storage. We want to avoid
     * making unnecessary network/API calls if the results for those calls already exist.
     * @param query The query which maps to the collection in storage that we want to check for duplicates
     * @param crawlResult JsonObject which contains the articles to be filtered
     * @return Single which emits the filtered crawlResult JsonObject
     */
    private Single<JsonObject> rxFilterExistingArticles(String query, JsonObject crawlResult) {

        return Observable.from(crawlResult.getJsonArray("value"))
                .map(article -> (JsonObject) article)
                .flatMap(article -> rxGetService(StorageService.name(), StorageService.class)
                        .flatMap(service -> {
                            String articleName = article.getString("name");
                            String articleDescription = article.getString("description");

                            return service.rxHasArticle(query, articleName, articleDescription);
                        })
                        .toObservable()
                        .filter(hasArticle -> !hasArticle)
                        .map(hasArticle -> article))
                .toList()
                .map(JsonArray::new)
                .map(array -> {
                    crawlResult.remove("value");
                    crawlResult.put("value", array);
                    return crawlResult;
                })
                .toSingle();
    }

    /**
     * Scans the JsonObject crawlResult for keyword entities, linking them back into the original document. Used to
     * determine the context of a crawl e.g. if 'apple' appears in the crawl result, it helps determine if it refers to
     * the company headquartered in Cupertino, or the actual fruit.
     * @param crawlResult The JsonObject to be scanned for keywords
     * @return Single that emits the entity linking response
     */
    private Single<JsonObject> rxLinkEntities(JsonObject crawlResult) {

        return rxGetService(NewsLinkerService.name(), NewsLinkerService.class)
                // We wrap the call inside a CircuitBreaker so that we can continually retry certain errors, knowing
                // that the underlying service won't actual get called until the circuit breaker closes again. This is
                // handy for 429 responses (API rate limits). We simply keep retrying knowing that the circuit breaker
                // is preventing unnecessary network calls
                .flatMap(service -> this.<JsonObject>rxExecuteCommand(newsLinkerBreaker, command ->
                        service.rxLinkEntities(crawlResult)
                                .doOnError(error -> handlerError(error, service::setTimeout))
                                .subscribe(RxHelper.toSubscriber(command.completer())))
                        .retryWhen(errors -> errors.flatMap(error -> {
                            // TODO: implement a more robust retry method
                            if (error.getMessage().contains("429") || error.getMessage().contains("open circuit")) {
                                return Observable.just(null).delay(200, TimeUnit.MILLISECONDS);
                            }

                            release(service);
                            return Observable.error(error);
                        })));
    }

    /**
     * Saves analysed articles to storage
     * @param query The query which represents the collection to save the article in to
     * @param article The JsonObject article to be saved
     * @return Single which emits the results of saving the article to storage
     */
    private Single<JsonObject> rxSaveAnalysedArticle(String query, JsonObject article) {
        return rxGetService(StorageService.name(), StorageService.class)
                .flatMap(service -> service.rxSaveArticles(query, new JsonArray().add(article))
                        .doOnEach(n -> release(service)));
    }

    /**
     * Helper method to handle errors thrown from the various service interfaces.
     * @param throwable Throwable representing the error to be handled
     * @param delayConsumer Consumer which wraps the service method call to set a timeout on retry attenmpts if requred.
     *                      Due to vert.x codegen reasons, these interfaces don't derive from a common interface, which
     *                      is why you don't simply see a common interface passed in in place of the consumer.
     */
    private void handlerError(Throwable throwable, Consumer<Long> delayConsumer) {
        if (throwable.getMessage().contains("error")) {
            JsonObject error = new JsonObject(throwable.getMessage()).getJsonObject("error");
            Integer errorCode = error.getInteger("statusCode");

            if (errorCode.equals(429)) {
                String errorMessage = error.getString("message");
                final long delay = Integer.parseInt(errorMessage.replaceAll("[^\\d]", "")) * 1000;
                delayConsumer.accept(delay);
            }
        }
    }

    /**
     * Releases the provided service object from ServiceDiscovery.
     * @param service The service object to be released.
     */
    private void release(Object service) {
        ServiceDiscovery.releaseServiceObject(serviceDiscovery, service);
    }


    /**
     * Helper method which wraps the result of a CircuitBreaker#executeCommand future object in a Single so that it can
     * be easily utilised in an rx chain.
     * @param breaker The CircuitBreaker to execute the command on.
     * @param command The operation to execute on the CircuitBreaker.
     * @param <T> The type of the expected future result. This also determines the type of the Single emission.
     * @return Single the emits the result of the command when executed on the provided CircuitBreaker.
     */
    private <T> Single<T> rxExecuteCommand(CircuitBreaker breaker, Handler<Future<T>> command) {

        return Single.create(new SingleOnSubscribeAdapter<T>(fut ->
                breaker.executeCommand(command, fut)));
    }

    /**
     * Helper method for retrieving a service from service discovery, wrapped in a Single so that it can be easily
     * utilised in an rx chain.
     * @param recordName The name of the record to filter available services by.
     * @param clazz The client class of the expected service.
     * @param <T> The type of the client class. This can be alternated between the rxjava or non-rx service class.
     * @return Single that emits the located service based on the recordName, if any.
     */
    private <T> Single<T> rxGetService(String recordName, Class<T> clazz) {

        return Single.create(new SingleOnSubscribeAdapter<>(fut ->
                EventBusService.getServiceProxy(serviceDiscovery, record -> record.getName().equals(recordName), clazz, fut)));
    }

}
