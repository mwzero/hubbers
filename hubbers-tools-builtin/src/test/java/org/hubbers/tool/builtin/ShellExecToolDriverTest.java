package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShellExecToolDriverTest {

    private ShellExecToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new ShellExecToolDriver(mapper);
        manifest = new ToolManifest();
        Map<String, Object> config = new HashMap<>();
        manifest.setConfig(config);
    }

    @Test
    void testToolType() {
        assertEquals("shell.exec", driver.type(), "Driver type should be shell.exec");
    }

    @Test
    void testEchoCommand() {
        ObjectNode input = mapper.createObjectNode();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        input.put("command", isWindows ? "echo hello" : "echo hello");
        input.put("timeoutSeconds", 10);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("exitCode").asInt(), "Exit code should be 0");
        assertTrue(result.path("stdout").asText().trim().contains("hello"), "Should capture stdout");
        assertFalse(result.path("timedOut").asBoolean(), "Should not time out");
    }

    @Test
    void testCommandWithWorkingDirectory() {
        ObjectNode input = mapper.createObjectNode();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        input.put("command", isWindows ? "cd" : "pwd");
        input.put("workingDir", tempDir.toString());
        input.put("timeoutSeconds", 10);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("exitCode").asInt());
        String stdout = result.path("stdout").asText().trim();
        assertTrue(stdout.contains(tempDir.getFileName().toString()),
            "Working directory should be reflected in output");
    }

    @Test
    void testCommandFailure() {
        ObjectNode input = mapper.createObjectNode();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        // A command that exits with non-zero
        input.put("command", isWindows ? "cmd /c exit 1" : "false");
        input.put("timeoutSeconds", 10);

        JsonNode result = driver.execute(manifest, input);

        assertNotEquals(0, result.path("exitCode").asInt(), "Should have non-zero exit code");
    }

    @Test
    void testStderrCapture() {
        ObjectNode input = mapper.createObjectNode();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        input.put("command", isWindows ? "echo error message 1>&2" : "echo error message >&2");
        input.put("timeoutSeconds", 10);

        JsonNode result = driver.execute(manifest, input);

        String stderr = result.path("stderr").asText().trim();
        assertTrue(stderr.contains("error message"), "Should capture stderr");
    }

    @Test
    void testMissingCommand() {
        ObjectNode input = mapper.createObjectNode();

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for missing command");
    }

    @Test
    void testPathTraversalInWorkingDir() {
        ObjectNode input = mapper.createObjectNode();
        input.put("command", "echo test");
        input.put("workingDir", "../etc");

        assertThrows(SecurityException.class,
            () -> driver.execute(manifest, input),
            "Should block path traversal in workingDir");
    }

    @Test
    void testTimeoutFromConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("default_timeout_seconds", "30");
        manifest.setConfig(config);

        ObjectNode input = mapper.createObjectNode();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        input.put("command", isWindows ? "echo done" : "echo done");

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("exitCode").asInt());
        assertFalse(result.path("timedOut").asBoolean());
    }

    @Test
    void testCommandWithEnvironmentVariables() {
        ObjectNode input = mapper.createObjectNode();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        input.put("command", isWindows ? "echo %TEST_VAR%" : "echo $TEST_VAR");
        ObjectNode env = mapper.createObjectNode();
        env.put("TEST_VAR", "hello_from_env");
        input.set("env", env);
        input.put("timeoutSeconds", 10);

        JsonNode result = driver.execute(manifest, input);

        assertEquals(0, result.path("exitCode").asInt());
        assertTrue(result.path("stdout").asText().trim().contains("hello_from_env"),
            "Should use injected environment variable");
    }
}
