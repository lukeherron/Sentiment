package com.gofish.sentiment.storage;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;

/**
 * @author Luke Herron
 */
@RunWith(VertxUnitRunner.class)
public class StorageServiceTest {

    @ClassRule
    public static final RunTestOnContext vertxRule = new RunTestOnContext();

    private Vertx vertx;
    private StorageService storageService;

    @BeforeClass
    public static void setUpDB(TestContext context) {
        // Prepare the DB with preset data. Start by removing all previously saved data
        final Vertx vertx = vertxRule.vertx();
        final MongoClient mongo = MongoClient.createShared(vertx, new JsonObject());
        final JsonObject command = new JsonObject().put("dropDatabase", 1);

        mongo.runCommand("dropDatabase", command, context.asyncAssertSuccess());

        // Then fill it with some demo data
        URL articlesURL = StorageServiceTest.class.getClassLoader().getResource("data/StorageArticles.json");
        assert  articlesURL != null;

        final String collectionName = "existingCollection";
        final String indexName = "existingCollectionIndex";
        final JsonArray articles = vertx.fileSystem().readFileBlocking(articlesURL.getFile()).toJsonArray();

        final JsonObject collectionIndex = new JsonObject()
                .put("name", 1)
                .put("description", 1);

        final JsonObject insertCommand = new JsonObject()
                .put("insert", collectionName)
                .put("documents", articles)
                .put("ordered", false);

        mongo.createCollection(collectionName, context.asyncAssertSuccess());
        mongo.createIndexWithOptions(collectionName, collectionIndex, new IndexOptions().name(indexName).unique(true), context.asyncAssertSuccess());
        mongo.runCommand("insert", insertCommand, context.asyncAssertSuccess());
        mongo.close();
    }

    @Before
    public void setUp() {
        vertx = vertxRule.vertx();
        storageService = StorageService.create(vertx, new JsonObject());
    }

    @Test
    public void testStorageServiceProxyIsCreated(TestContext context) {
        StorageService storageService = StorageService.createProxy(vertx, StorageService.ADDRESS);

        context.assertNotNull(storageService);
    }

    @Test
    public void testCreateCollectionSucceeds(TestContext context) {
        storageService.createCollection("testCollection", context.asyncAssertSuccess());
    }

    @Test
    public void testCreateCollectionFailsIfCollectionAlreadyExists(TestContext context) {
        storageService.createCollection("existingCollection", context.asyncAssertFailure(result ->
                context.assertEquals("Collection already exists", result.getMessage())));
    }

    @Test
    public void testCreateIndexSucceeds(TestContext context) {
        JsonObject index = new JsonObject().put("test", 1);
        storageService.createCollection("testIndexCollection", context.asyncAssertSuccess());
        storageService.createIndex("testIndexCollection", index, context.asyncAssertSuccess());
    }

    @Test
    public void testCreateIndexFailsIfIndexKeyEmpty(TestContext context) {
        storageService.createIndex("testCollection", new JsonObject(), context.asyncAssertFailure(result ->
                context.assertTrue(result.getMessage().contains("Index keys cannot be empty"))));
    }

    @Test
    public void testGetCollectionsSucceeds(TestContext context) {
        storageService.getCollections(context.asyncAssertSuccess(result -> context.assertFalse(result.isEmpty())));
    }

    @Test
    public void testGetSentimentResultsSucceedsForValidCollection(TestContext context) {
        storageService.getSentimentResults("existingCollection", context.asyncAssertSuccess(result ->
                context.assertEquals("{\"score\":0.38630980000000004}", result.encode())));
    }

    @Test
    public void testGetSentimentResultsFailsForInvalidCollection(TestContext context) {
        storageService.getSentimentResults("invalidCollection", context.asyncAssertSuccess(result ->
                context.assertTrue(result.isEmpty())));
    }

    @Test
    public void testHasCollectionReturnsTrueIfCollectionExists(TestContext context) {
        storageService.hasCollection("existingCollection", context.asyncAssertSuccess(context::assertTrue));
    }

    @Test
    public void testHasCollectionReturnsFalseIfCollectionDoesNotExist(TestContext context) {
        storageService.hasCollection("missingCollection", context.asyncAssertSuccess(context::assertFalse));
    }

    @Test
    public void testSaveArticlesSucceeds(TestContext context) {
        URL articlesURL = StorageServiceTest.class.getClassLoader().getResource("data/StorageArticleSingle.json");
        assert  articlesURL != null;

        final JsonArray article = vertx.fileSystem().readFileBlocking(articlesURL.getFile()).toJsonArray();

        storageService.saveArticles("existingCollection", article, context.asyncAssertSuccess(result ->
                context.assertEquals("{\"ok\":1,\"n\":1}", result.encode())));
    }

    @Test
    public void testSaveArticlesHasWriteErrorsWhenSavingDuplicates(TestContext context) {
        URL articlesURL = StorageServiceTest.class.getClassLoader().getResource("data/StorageArticles.json");
        assert  articlesURL != null;

        final JsonArray articles = vertx.fileSystem().readFileBlocking(articlesURL.getFile()).toJsonArray();

        storageService.saveArticles("existingCollection", articles, context.asyncAssertSuccess(result ->
                context.assertTrue(result.containsKey("writeErrors"))));
    }

    @Test
    public void testIsIndexPresentReturnsTrueWhenCollectionExists(TestContext context) {
        storageService.isIndexPresent("existingCollectionIndex", "existingCollection", context.asyncAssertSuccess(context::assertTrue));
    }

    @Test
    public void testIsIndexPresentReturnsFalseWhenCollectionDoesNotExist(TestContext context) {
        storageService.isIndexPresent("missingCollectionIndex", "missingCollection", context.asyncAssertSuccess(context::assertFalse));
    }
}
