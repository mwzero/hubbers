package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LuceneKvToolDriverTest {

    private LuceneKvToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new LuceneKvToolDriver(mapper);
        
        // Create test manifest
        manifest = new ToolManifest();
        Map<String, Object> config = new HashMap<>();
        config.put("index_path", tempDir.resolve("test-kv-index").toString());
        manifest.setConfig(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup temp directory
        Files.walk(tempDir)
            .sorted((a, b) -> -a.compareTo(b))
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // Ignore
                }
            });
    }

    @Test
    void testToolType() {
        assertEquals("lucene.kv", driver.type(), "Driver type should be lucene.kv");
    }

    @Test
    void testPutOperation() throws IOException {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "put");
        input.put("key", "user:1001");
        ObjectNode value = mapper.createObjectNode();
        value.put("name", "Mario Rossi");
        value.put("email", "mario@example.com");
        input.set("value", value);

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean(), "Put operation should succeed");
        assertEquals("put", result.path("operation").asText());
        assertEquals("user:1001", result.path("key").asText());
        assertEquals("Mario Rossi", result.path("value").path("name").asText());
    }

    @Test
    void testGetOperation() throws IOException {
        // First put a value
        ObjectNode putInput = mapper.createObjectNode();
        putInput.put("operation", "put");
        putInput.put("key", "user:1002");
        ObjectNode value = mapper.createObjectNode();
        value.put("name", "Laura Bianchi");
        value.put("age", 30);
        putInput.set("value", value);
        driver.execute(manifest, putInput);

        // Then get it
        ObjectNode getInput = mapper.createObjectNode();
        getInput.put("operation", "get");
        getInput.put("key", "user:1002");

        JsonNode result = driver.execute(manifest, getInput);

        assertTrue(result.path("success").asBoolean(), "Get operation should succeed");
        assertEquals("get", result.path("operation").asText());
        assertEquals("user:1002", result.path("key").asText());
        assertEquals("Laura Bianchi", result.path("value").path("name").asText());
        assertEquals(30, result.path("value").path("age").asInt());
    }

    @Test
    void testGetNonExistentKey() throws IOException {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "get");
        input.put("key", "non-existent-key");

        JsonNode result = driver.execute(manifest, input);

        assertFalse(result.path("success").asBoolean(), "Get should fail for non-existent key");
        assertTrue(result.path("value").isNull(), "Value should be null for non-existent key");
        assertTrue(result.path("error").asText().contains("not found"), 
                "Error message should indicate key not found");
    }

    @Test
    void testDeleteOperation() throws IOException {
        // First put a value
        ObjectNode putInput = mapper.createObjectNode();
        putInput.put("operation", "put");
        putInput.put("key", "user:1003");
        ObjectNode value = mapper.createObjectNode();
        value.put("name", "Giovanni Verdi");
        putInput.set("value", value);
        driver.execute(manifest, putInput);

        // Then delete it
        ObjectNode deleteInput = mapper.createObjectNode();
        deleteInput.put("operation", "delete");
        deleteInput.put("key", "user:1003");

        JsonNode result = driver.execute(manifest, deleteInput);

        assertTrue(result.path("success").asBoolean(), "Delete operation should succeed");
        assertEquals("delete", result.path("operation").asText());

        // Verify it's deleted
        ObjectNode getInput = mapper.createObjectNode();
        getInput.put("operation", "get");
        getInput.put("key", "user:1003");
        JsonNode getResult = driver.execute(manifest, getInput);
        assertFalse(getResult.path("success").asBoolean(), 
                "Get should fail after delete");
    }

    @Test
    void testBatchPutOperation() throws IOException {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "batch_put");
        
        ArrayNode items = mapper.createArrayNode();
        for (int i = 1; i <= 3; i++) {
            ObjectNode item = mapper.createObjectNode();
            item.put("key", "product:" + i);
            ObjectNode value = mapper.createObjectNode();
            value.put("name", "Product " + i);
            value.put("price", i * 10.0);
            item.set("value", value);
            items.add(item);
        }
        input.set("items", items);

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean(), "Batch put should succeed");
        assertEquals("batch_put", result.path("operation").asText());
        assertEquals(3, result.path("count").asInt(), "Should insert 3 items");

        // Verify items were inserted
        for (int i = 1; i <= 3; i++) {
            ObjectNode getInput = mapper.createObjectNode();
            getInput.put("operation", "get");
            getInput.put("key", "product:" + i);
            JsonNode getResult = driver.execute(manifest, getInput);
            assertTrue(getResult.path("success").asBoolean(), 
                    "Product " + i + " should exist");
        }
    }

    @Test
    void testListKeysOperation() throws IOException {
        // Insert some items
        for (int i = 1; i <= 5; i++) {
            ObjectNode putInput = mapper.createObjectNode();
            putInput.put("operation", "put");
            putInput.put("key", "item:" + i);
            ObjectNode value = mapper.createObjectNode();
            value.put("data", "value" + i);
            putInput.set("value", value);
            driver.execute(manifest, putInput);
        }

        // List keys
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list_keys");
        input.put("limit", 10);

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean(), "List keys should succeed");
        assertEquals("list_keys", result.path("operation").asText());
        assertEquals(5, result.path("count").asInt(), "Should list 5 keys");
        assertTrue(result.path("keys").isArray(), "Keys should be an array");
        assertEquals(5, result.path("keys").size(), "Keys array should contain 5 elements");
    }

    @Test
    void testListKeysWithLimit() throws IOException {
        // Insert 10 items
        for (int i = 1; i <= 10; i++) {
            ObjectNode putInput = mapper.createObjectNode();
            putInput.put("operation", "put");
            putInput.put("key", "item:" + i);
            ObjectNode value = mapper.createObjectNode();
            value.put("data", "value" + i);
            putInput.set("value", value);
            driver.execute(manifest, putInput);
        }

        // List with limit of 5
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list_keys");
        input.put("limit", 5);

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean());
        assertTrue(result.path("count").asInt() <= 5, "Should return at most 5 keys");
    }

    @Test
    void testListKeysEmptyIndex() throws IOException {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list_keys");

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean(), "List keys should succeed even on empty index");
        assertEquals(0, result.path("count").asInt(), "Should return 0 keys");
        assertTrue(result.path("keys").isArray(), "Keys should be an array");
        assertEquals(0, result.path("keys").size(), "Keys array should be empty");
    }

    @Test
    void testMissingOperationThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        // No operation field

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.execute(manifest, input);
        });

        assertTrue(exception.getMessage().contains("operation"), 
                "Error should mention missing operation");
    }

    @Test
    void testInvalidOperationThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "invalid-operation");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.execute(manifest, input);
        });

        assertTrue(exception.getMessage().contains("Unknown operation"), 
                "Error should mention unknown operation");
    }

    @Test
    void testPutWithoutKeyThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "put");
        ObjectNode value = mapper.createObjectNode();
        value.put("name", "Test");
        input.set("value", value);
        // Missing key

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.execute(manifest, input);
        });

        assertTrue(exception.getMessage().contains("key"), 
                "Error should mention missing key");
    }

    @Test
    void testPutWithoutValueThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "put");
        input.put("key", "test-key");
        // Missing value

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.execute(manifest, input);
        });

        assertTrue(exception.getMessage().contains("value"), 
                "Error should mention missing value");
    }

    @Test
    void testComplexValueStorage() throws IOException {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "put");
        input.put("key", "complex:1");
        
        // Create complex nested value
        ObjectNode value = mapper.createObjectNode();
        value.put("name", "Complex Object");
        value.put("active", true);
        value.put("count", 42);
        ArrayNode tags = mapper.createArrayNode();
        tags.add("tag1");
        tags.add("tag2");
        value.set("tags", tags);
        ObjectNode nested = mapper.createObjectNode();
        nested.put("nested_field", "nested_value");
        value.set("metadata", nested);
        
        input.set("value", value);

        JsonNode putResult = driver.execute(manifest, input);
        assertTrue(putResult.path("success").asBoolean());

        // Retrieve and verify
        ObjectNode getInput = mapper.createObjectNode();
        getInput.put("operation", "get");
        getInput.put("key", "complex:1");
        
        JsonNode getResult = driver.execute(manifest, getInput);
        assertTrue(getResult.path("success").asBoolean());
        JsonNode retrievedValue = getResult.path("value");
        
        assertEquals("Complex Object", retrievedValue.path("name").asText());
        assertTrue(retrievedValue.path("active").asBoolean());
        assertEquals(42, retrievedValue.path("count").asInt());
        assertEquals(2, retrievedValue.path("tags").size());
        assertEquals("nested_value", retrievedValue.path("metadata").path("nested_field").asText());
    }

    @Test
    void testUpdateExistingKey() throws IOException {
        String key = "update-test:1";
        
        // Insert initial value
        ObjectNode putInput1 = mapper.createObjectNode();
        putInput1.put("operation", "put");
        putInput1.put("key", key);
        ObjectNode value1 = mapper.createObjectNode();
        value1.put("version", 1);
        putInput1.set("value", value1);
        driver.execute(manifest, putInput1);

        // Update with new value
        ObjectNode putInput2 = mapper.createObjectNode();
        putInput2.put("operation", "put");
        putInput2.put("key", key);
        ObjectNode value2 = mapper.createObjectNode();
        value2.put("version", 2);
        putInput2.set("value", value2);
        driver.execute(manifest, putInput2);

        // Verify updated value
        ObjectNode getInput = mapper.createObjectNode();
        getInput.put("operation", "get");
        getInput.put("key", key);
        
        JsonNode result = driver.execute(manifest, getInput);
        assertEquals(2, result.path("value").path("version").asInt(), 
                "Value should be updated to version 2");
    }
}
