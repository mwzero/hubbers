package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessManageToolDriverTest {

    private ProcessManageToolDriver driver;
    private ObjectMapper mapper;
    private ToolManifest manifest;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        driver = new ProcessManageToolDriver(mapper);
        manifest = new ToolManifest();
        Map<String, Object> config = new HashMap<>();
        manifest.setConfig(config);
    }

    @Test
    void testToolType() {
        assertEquals("process.manage", driver.type(), "Driver type should be process.manage");
    }

    @Test
    void testListProcesses() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list");

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean(), "List should succeed");
        assertTrue(result.path("processes").isArray(), "Should return processes array");
        assertTrue(result.path("processes").size() > 0, "Should have at least one process");
        
        // Verify structure of first process
        JsonNode first = result.path("processes").get(0);
        assertTrue(first.has("pid"), "Process should have pid");
    }

    @Test
    void testListWithNamePattern() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "list");
        input.put("namePattern", "java");

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean());
        // We're running Java tests, so at least one java process should exist
        assertTrue(result.path("processes").size() > 0, "Should find at least one java process");
    }

    @Test
    void testInfoCurrentProcess() {
        long currentPid = ProcessHandle.current().pid();

        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "info");
        input.put("pid", currentPid);

        JsonNode result = driver.execute(manifest, input);

        assertTrue(result.path("success").asBoolean(), "Info should succeed for current process");
        assertEquals(currentPid, result.path("info").path("pid").asLong());
    }

    @Test
    void testInfoNonExistentProcess() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "info");
        input.put("pid", 999999999L);

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for non-existent PID");
    }

    @Test
    void testInfoMissingPid() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "info");

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw when pid is missing");
    }

    @Test
    void testKillNonExistentProcess() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "kill");
        input.put("pid", 999999999L);

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for non-existent PID");
    }

    @Test
    void testMissingOperation() {
        ObjectNode input = mapper.createObjectNode();

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw when operation is missing");
    }

    @Test
    void testUnsupportedOperation() {
        ObjectNode input = mapper.createObjectNode();
        input.put("operation", "restart");

        assertThrows(IllegalArgumentException.class,
            () -> driver.execute(manifest, input),
            "Should throw for unsupported operation");
    }
}
