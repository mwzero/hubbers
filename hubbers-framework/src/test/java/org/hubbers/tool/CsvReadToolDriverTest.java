package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvReadToolDriverTest {

    private CsvReadToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new CsvReadToolDriver(mapper);
        manifest = new ToolManifest();
        Map<String, Object> config = new HashMap<>();
        config.put("default_file_path", tempDir.resolve("default.csv").toString());
        manifest.setConfig(config);
    }

    @Test
    void testToolType() {
        assertEquals("csv.read", driver.type(), "Driver type should be csv.read");
    }

    @Test
    void testReadSimpleCsv() throws Exception {
        Path csvFile = tempDir.resolve("simple.csv");
        Files.write(csvFile, List.of(
            "title,source",
            "Article One,ANSA",
            "Article Two,BBC"
        ), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", csvFile.toString());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(2, result.path("rows").asInt(), "Should have 2 data rows");
        assertTrue(result.path("items").isArray(), "Items should be an array");
        assertEquals(2, result.path("items").size());
        assertEquals("Article One", result.path("items").get(0).path("title").asText());
        assertEquals("ANSA", result.path("items").get(0).path("source").asText());
        assertEquals("Article Two", result.path("items").get(1).path("title").asText());
        assertEquals("BBC", result.path("items").get(1).path("source").asText());
    }

    @Test
    void testReadCsvWithQuotedFields() throws Exception {
        Path csvFile = tempDir.resolve("quoted.csv");
        Files.write(csvFile, List.of(
            "title,description",
            "\"Has, comma\",Normal text",
            "Simple,\"Has \"\"quotes\"\"\""
        ), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", csvFile.toString());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(2, result.path("rows").asInt());
        assertEquals("Has, comma", result.path("items").get(0).path("title").asText(),
            "Should handle quoted fields with commas");
        assertEquals("Has \"quotes\"", result.path("items").get(1).path("description").asText(),
            "Should handle escaped quotes");
    }

    @Test
    void testReadEmptyCsv() throws Exception {
        Path csvFile = tempDir.resolve("empty.csv");
        Files.write(csvFile, List.of(), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", csvFile.toString());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("rows").asInt(), "Empty CSV should have 0 rows");
        assertTrue(result.path("items").isArray());
        assertEquals(0, result.path("items").size());
    }

    @Test
    void testReadHeaderOnly() throws Exception {
        Path csvFile = tempDir.resolve("headeronly.csv");
        Files.write(csvFile, List.of("col1,col2"), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", csvFile.toString());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("rows").asInt(), "Header-only CSV should have 0 rows");
    }

    @Test
    void testReadFileNotFound() {
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("nonexistent.csv").toString());

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for non-existent file");
    }

    @Test
    void testReadMissingFilePath() {
        ToolManifest noConfig = new ToolManifest();
        ObjectNode input = mapper.createObjectNode();

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(noConfig, input),
            "Should throw when file_path is missing and no default configured");
    }

    @Test
    void testReadUsesDefaultFilePath() throws Exception {
        Path defaultCsv = tempDir.resolve("default.csv");
        Files.write(defaultCsv, List.of("name", "test"), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();

        JsonNode result = driver.execute(manifest, input);

        assertEquals(1, result.path("rows").asInt(), "Should read from default file path");
    }

    @Test
    void testReadCsvWithEmptyLines() throws Exception {
        Path csvFile = tempDir.resolve("blanks.csv");
        Files.write(csvFile, List.of(
            "name,value",
            "one,1",
            "",
            "two,2"
        ), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", csvFile.toString());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(2, result.path("rows").asInt(), "Should skip blank lines");
    }

    @Test
    void testReadCsvWithMissingColumns() throws Exception {
        Path csvFile = tempDir.resolve("missing.csv");
        Files.write(csvFile, List.of(
            "a,b,c",
            "1,2"
        ), StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", csvFile.toString());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(1, result.path("rows").asInt());
        assertEquals("1", result.path("items").get(0).path("a").asText());
        assertEquals("2", result.path("items").get(0).path("b").asText());
        assertEquals("", result.path("items").get(0).path("c").asText(),
            "Missing column should be empty string");
    }

    @Test
    void testWriteThenReadRoundTrip() throws Exception {
        // Write with CsvWriteToolDriver, then read back
        CsvWriteToolDriver writer = new CsvWriteToolDriver(mapper);
        ObjectNode writeInput = mapper.createObjectNode();
        writeInput.put("file_path", tempDir.resolve("roundtrip.csv").toString());
        var items = mapper.createArrayNode();
        var row = mapper.createObjectNode();
        row.put("title", "Test Article");
        row.put("source", "Unit Test");
        row.put("score", "0.95");
        items.add(row);
        writeInput.set("items", items);
        writer.execute(manifest, writeInput);

        // Read back
        ObjectNode readInput = mapper.createObjectNode();
        readInput.put("file_path", tempDir.resolve("roundtrip.csv").toString());
        JsonNode result = driver.execute(manifest, readInput);

        assertEquals(1, result.path("rows").asInt());
        assertEquals("Test Article", result.path("items").get(0).path("title").asText());
        assertEquals("Unit Test", result.path("items").get(0).path("source").asText());
        assertEquals("0.95", result.path("items").get(0).path("score").asText());
    }
}
