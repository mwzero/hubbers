package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

class CsvWriteToolDriverTest {

    private CsvWriteToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new CsvWriteToolDriver(mapper);
        manifest = new ToolManifest();
        Map<String, Object> config = new HashMap<>();
        config.put("default_file_path", tempDir.resolve("default.csv").toString());
        manifest.setConfig(config);
    }

    @Test
    void testToolType() {
        assertEquals("csv.write", driver.type(), "Driver type should be csv.write");
    }

    @Test
    void testWriteSimpleCsv() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("output.csv").toString());
        ArrayNode items = mapper.createArrayNode();
        ObjectNode row1 = mapper.createObjectNode();
        row1.put("title", "Article One");
        row1.put("source", "Test Source");
        items.add(row1);
        ObjectNode row2 = mapper.createObjectNode();
        row2.put("title", "Article Two");
        row2.put("source", "Other Source");
        items.add(row2);
        input.set("items", items);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(2, result.path("rows").asInt(), "Should report 2 rows");
        assertTrue(result.path("file_path").asText().contains("output.csv"), "Should contain file path");

        List<String> lines = Files.readAllLines(tempDir.resolve("output.csv"), StandardCharsets.UTF_8);
        assertEquals(3, lines.size(), "Should have header + 2 data rows");
        assertEquals("title,source", lines.get(0), "Header row should match field names");
        assertEquals("Article One,Test Source", lines.get(1));
        assertEquals("Article Two,Other Source", lines.get(2));
    }

    @Test
    void testWriteWithSpecialCharacters() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("special.csv").toString());
        ArrayNode items = mapper.createArrayNode();
        ObjectNode row = mapper.createObjectNode();
        row.put("title", "Contains, comma");
        row.put("desc", "Has \"quotes\"");
        items.add(row);
        input.set("items", items);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(1, result.path("rows").asInt());
        List<String> lines = Files.readAllLines(tempDir.resolve("special.csv"), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("\"Contains, comma\""), "Comma values should be quoted");
        assertTrue(lines.get(1).contains("\"Has \"\"quotes\"\"\""), "Quotes should be escaped");
    }

    @Test
    void testWriteEmptyItems() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("empty.csv").toString());
        input.set("items", mapper.createArrayNode());

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("rows").asInt(), "Should report 0 rows");
        assertTrue(Files.exists(tempDir.resolve("empty.csv")), "File should still be created");
    }

    @Test
    void testWriteUsesDefaultFilePath() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        ArrayNode items = mapper.createArrayNode();
        ObjectNode row = mapper.createObjectNode();
        row.put("name", "test");
        items.add(row);
        input.set("items", items);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(1, result.path("rows").asInt());
        assertTrue(Files.exists(tempDir.resolve("default.csv")), "Should use default file path from config");
    }

    @Test
    void testWriteMissingFilePath() {
        ToolManifest noConfig = new ToolManifest();
        ObjectNode input = mapper.createObjectNode();
        input.set("items", mapper.createArrayNode());

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(noConfig, input),
            "Should throw when file_path is missing and no default configured");
    }

    @Test
    void testWriteCreatesParentDirectories() throws Exception {
        Path nested = tempDir.resolve("sub").resolve("dir").resolve("output.csv");
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", nested.toString());
        ArrayNode items = mapper.createArrayNode();
        ObjectNode row = mapper.createObjectNode();
        row.put("col", "val");
        items.add(row);
        input.set("items", items);

        driver.execute(manifest, input);

        assertTrue(Files.exists(nested), "Should create parent directories");
    }

    @Test
    void testWriteWithMixedFields() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("mixed.csv").toString());
        ArrayNode items = mapper.createArrayNode();
        ObjectNode row1 = mapper.createObjectNode();
        row1.put("a", "1");
        row1.put("b", "2");
        items.add(row1);
        ObjectNode row2 = mapper.createObjectNode();
        row2.put("b", "3");
        row2.put("c", "4");
        items.add(row2);
        input.set("items", items);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(2, result.path("rows").asInt());
        List<String> lines = Files.readAllLines(tempDir.resolve("mixed.csv"), StandardCharsets.UTF_8);
        assertEquals("a,b,c", lines.get(0), "Headers should include all unique fields");
        assertEquals("1,2,", lines.get(1), "Missing fields should be empty");
        assertEquals(",3,4", lines.get(2));
    }

    @Test
    void testWriteWithNullValues() throws Exception {
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("nulls.csv").toString());
        ArrayNode items = mapper.createArrayNode();
        ObjectNode row = mapper.createObjectNode();
        row.put("name", "test");
        row.putNull("value");
        items.add(row);
        input.set("items", items);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(1, result.path("rows").asInt());
        List<String> lines = Files.readAllLines(tempDir.resolve("nulls.csv"), StandardCharsets.UTF_8);
        assertEquals("name,value", lines.get(0));
        assertEquals("test,", lines.get(1), "Null values should be empty strings");
    }
}
