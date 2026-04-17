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

import static org.junit.jupiter.api.Assertions.*;

class FileOpsToolDriverTest {

    private FileOpsToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new FileOpsToolDriver(mapper);
        manifest = new ToolManifest();
    }

    @Test
    void testToolType() {
        assertEquals("file.ops", driver.type(), "Driver type should be file.ops");
    }

    // --- Write & Read ---

    @Test
    void testWriteAndReadFile() throws Exception {
        Path file = tempDir.resolve("test.txt");
        ObjectNode writeInput = mapper.createObjectNode();
        writeInput.put("operation", "write");
        writeInput.put("path", file.toString());
        writeInput.put("content", "Hello World");

        JsonNode writeResult = driver.execute(manifest, writeInput);
        assertTrue(writeResult.path("success").asBoolean(), "Write should succeed");
        assertTrue(Files.exists(file), "File should exist after write");

        ObjectNode readInput = mapper.createObjectNode();
        readInput.put("operation", "read");
        readInput.put("path", file.toString());

        JsonNode readResult = driver.execute(manifest, readInput);
        assertTrue(readResult.path("success").asBoolean(), "Read should succeed");
        assertEquals("Hello World", readResult.path("content").asText());
    }

    @Test
    void testWriteCreatesParentDirectories() throws Exception {
        Path file = tempDir.resolve("sub").resolve("dir").resolve("file.txt");
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "write");
        input.put("path", file.toString());
        input.put("content", "nested");

        driver.execute(manifest, input);

        assertTrue(Files.exists(file), "Should create parent directories");
        assertEquals("nested", Files.readString(file, StandardCharsets.UTF_8));
    }

    // --- Append ---

    @Test
    void testAppendToFile() throws Exception {
        Path file = tempDir.resolve("append.txt");
        Files.writeString(file, "line1\n", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "append");
        input.put("path", file.toString());
        input.put("content", "line2\n");

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertEquals("line1\nline2\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void testAppendToNonExistentFile() throws Exception {
        Path file = tempDir.resolve("new-append.txt");
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "append");
        input.put("path", file.toString());
        input.put("content", "first content");

        driver.execute(manifest, input);

        assertEquals("first content", Files.readString(file, StandardCharsets.UTF_8));
    }

    // --- Copy ---

    @Test
    void testCopyFile() throws Exception {
        Path source = tempDir.resolve("source.txt");
        Path dest = tempDir.resolve("dest.txt");
        Files.writeString(source, "copy me", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "copy");
        input.put("path", source.toString());
        input.put("destination", dest.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertTrue(Files.exists(source), "Source should still exist");
        assertEquals("copy me", Files.readString(dest, StandardCharsets.UTF_8));
    }

    // --- Move ---

    @Test
    void testMoveFile() throws Exception {
        Path source = tempDir.resolve("moveme.txt");
        Path dest = tempDir.resolve("moved.txt");
        Files.writeString(source, "move me", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "move");
        input.put("path", source.toString());
        input.put("destination", dest.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertFalse(Files.exists(source), "Source should no longer exist");
        assertEquals("move me", Files.readString(dest, StandardCharsets.UTF_8));
    }

    // --- Delete ---

    @Test
    void testDeleteFile() throws Exception {
        Path file = tempDir.resolve("deleteme.txt");
        Files.writeString(file, "bye", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "delete");
        input.put("path", file.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertFalse(Files.exists(file), "File should be deleted");
    }

    @Test
    void testDeleteDirectory() throws Exception {
        Path dir = tempDir.resolve("deldir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("child.txt"), "data", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "delete");
        input.put("path", dir.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertFalse(Files.exists(dir), "Directory should be recursively deleted");
    }

    // --- List ---

    @Test
    void testListDirectory() throws Exception {
        Path dir = tempDir.resolve("listdir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("a.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.csv"), "b", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list");
        input.put("path", dir.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertEquals(2, result.path("items").size(), "Should list 2 files");
    }

    @Test
    void testListDirectoryWithPattern() throws Exception {
        Path dir = tempDir.resolve("patterndir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("file1.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("file2.txt"), "b", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("data.csv"), "c", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list");
        input.put("path", dir.toString());
        input.put("pattern", ".*\\.txt");

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertEquals(2, result.path("items").size(), "Should only list .txt files");
    }

    // --- Mkdir ---

    @Test
    void testMkdir() throws Exception {
        Path dir = tempDir.resolve("newdir").resolve("subdir");
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "mkdir");
        input.put("path", dir.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertTrue(Files.isDirectory(dir), "Directory should be created");
    }

    // --- Exists ---

    @Test
    void testExistsTrue() throws Exception {
        Path file = tempDir.resolve("exists.txt");
        Files.writeString(file, "data", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "exists");
        input.put("path", file.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("info").path("exists").asBoolean(), "Should report exists=true");
    }

    @Test
    void testExistsFalse() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "exists");
        input.put("path", tempDir.resolve("nope.txt").toString());

        JsonNode result = driver.execute(manifest, input);
        assertFalse(result.path("info").path("exists").asBoolean(), "Should report exists=false");
    }

    // --- Info ---

    @Test
    void testFileInfo() throws Exception {
        Path file = tempDir.resolve("info.txt");
        Files.writeString(file, "hello world", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "info");
        input.put("path", file.toString());

        JsonNode result = driver.execute(manifest, input);
        assertTrue(result.path("success").asBoolean());
        assertTrue(result.path("info").path("isFile").asBoolean());
        assertFalse(result.path("info").path("isDirectory").asBoolean());
        assertTrue(result.path("info").path("size").asLong() > 0, "File size should be > 0");
    }

    // --- Error cases ---

    @Test
    void testReadNonExistentFile() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "read");
        input.put("path", tempDir.resolve("nope.txt").toString());

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for non-existent file");
    }

    @Test
    void testMissingOperation() {
        ObjectNode input = mapper.createObjectNode();
        input.put("path", tempDir.resolve("x.txt").toString());

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for missing operation");
    }

    @Test
    void testUnsupportedOperation() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "invalid_op");
        input.put("path", tempDir.resolve("x.txt").toString());

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for unsupported operation");
    }

    @Test
    void testPathTraversalBlocked() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "read");
        input.put("path", tempDir.resolve("..").resolve("etc").resolve("passwd").toString());

        assertThrows(SecurityException.class,
            () -> driver.execute(manifest, input),
            "Should block path traversal");
    }

    @Test
    void testCopyPathTraversalInDestination() throws Exception {
        Path source = tempDir.resolve("src.txt");
        Files.writeString(source, "data", StandardCharsets.UTF_8);

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "copy");
        input.put("path", source.toString());
        input.put("destination", "../etc/malicious.txt");

        assertThrows(SecurityException.class,
            () -> driver.execute(manifest, input),
            "Should block path traversal in destination");
    }
}
