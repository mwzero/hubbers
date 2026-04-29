package org.hubbers.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.mcp.protocol.McpRequest;
import org.hubbers.mcp.protocol.McpResponse;
import org.hubbers.mcp.protocol.McpToolCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpRequestHandler Tests")
class McpRequestHandlerTest {

    private ObjectMapper mapper;
    private McpRequestHandler handler;
    private StubRuntimeFacade stubFacade;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();

        // Stub artifact repository with empty lists
        var stubRepo = new StubArtifactRepository();

        var toolProvider = new McpToolProvider(stubRepo, mapper);
        var promptProvider = new McpPromptProvider(stubRepo);
        stubFacade = new StubRuntimeFacade();
        handler = new McpRequestHandler(toolProvider, promptProvider, stubFacade, mapper);
    }

    @Test
    @DisplayName("Should handle initialize and return server capabilities")
    void testInitialize_ReturnsCapabilities() throws Exception {
        McpRequest request = buildRequest(1, "initialize", mapper.createObjectNode());

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        McpResponse resp = response.get();
        assertNull(resp.getError());
        assertNotNull(resp.getResult());

        // Verify result contains expected fields
        JsonNode resultNode = mapper.valueToTree(resp.getResult());
        assertTrue(resultNode.has("protocolVersion"));
        assertTrue(resultNode.has("capabilities"));
        assertTrue(resultNode.has("serverInfo"));
        assertEquals("hubbers", resultNode.get("serverInfo").get("name").asText());
    }

    @Test
    @DisplayName("Should return empty for notifications (no response)")
    void testNotification_ReturnsEmpty() {
        McpRequest request = buildRequest(null, "notifications/initialized", null);

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isEmpty());
    }

    @Test
    @DisplayName("Should handle tools/list and return tool array")
    void testToolsList_ReturnsToolArray() throws Exception {
        McpRequest request = buildRequest(2, "tools/list", mapper.createObjectNode());

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        McpResponse resp = response.get();
        assertNull(resp.getError());

        JsonNode resultNode = mapper.valueToTree(resp.getResult());
        assertTrue(resultNode.has("tools"));
        assertTrue(resultNode.get("tools").isArray());
    }

    @Test
    @DisplayName("Should handle tools/call with valid tool name")
    void testToolsCall_WithValidTool_ExecutesAndReturnsResult() throws Exception {
        // Set up stub to return success
        stubFacade.setToolResult(RunResult.success(mapper.readTree("{\"result\": \"done\"}")));

        ObjectNode params = mapper.createObjectNode();
        params.put("name", "tool.test.tool");
        params.set("arguments", mapper.createObjectNode().put("key", "value"));

        McpRequest request = buildRequest(3, "tools/call", params);

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        McpResponse resp = response.get();
        assertNull(resp.getError());
        assertEquals("test.tool", stubFacade.getLastToolName());
    }

    @Test
    @DisplayName("Should handle tools/call with pipeline prefix")
    void testToolsCall_WithPipelinePrefix_RoutesToPipeline() throws Exception {
        stubFacade.setPipelineResult(RunResult.success(mapper.readTree("{\"ok\": true}")));

        ObjectNode params = mapper.createObjectNode();
        params.put("name", "pipeline.file.backup");
        params.set("arguments", mapper.createObjectNode());

        McpRequest request = buildRequest(4, "tools/call", params);
        handler.handle(request);

        assertEquals("file.backup", stubFacade.getLastPipelineName());
    }

    @Test
    @DisplayName("Should handle tools/call with agent prefix")
    void testToolsCall_WithAgentPrefix_RoutesToAgent() throws Exception {
        stubFacade.setAgentResult(RunResult.success(mapper.readTree("{\"answer\": \"42\"}")));

        ObjectNode params = mapper.createObjectNode();
        params.put("name", "agent.universal.task");
        params.set("arguments", mapper.createObjectNode().put("request", "hello"));

        McpRequest request = buildRequest(5, "tools/call", params);
        handler.handle(request);

        assertEquals("universal.task", stubFacade.getLastAgentName());
    }

    @Test
    @DisplayName("Should reject skill prefix in tools/call (skills are prompts, not tools)")
    void testToolsCall_WithSkillPrefix_ReturnsError() throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "skill.code.review");
        params.set("arguments", mapper.createObjectNode().put("input", "review this"));

        McpRequest request = buildRequest(6, "tools/call", params);

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        JsonNode resultNode = mapper.valueToTree(response.get().getResult());
        assertTrue(resultNode.get("isError").asBoolean());
    }

    @Test
    @DisplayName("Should return error for unknown tool prefix")
    void testToolsCall_WithUnknownPrefix_ReturnsError() throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "unknown.thing");
        params.set("arguments", mapper.createObjectNode());

        McpRequest request = buildRequest(7, "tools/call", params);

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        // The error is returned as a McpToolCallResult with isError=true inside the result
        JsonNode resultNode = mapper.valueToTree(response.get().getResult());
        assertTrue(resultNode.get("isError").asBoolean());
    }

    @Test
    @DisplayName("Should return error for tools/call with missing name")
    void testToolsCall_WithMissingName_ReturnsInvalidParams() {
        McpRequest request = buildRequest(8, "tools/call", mapper.createObjectNode());

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        assertNotNull(response.get().getError());
        assertEquals(-32602, response.get().getError().getCode());
    }

    @Test
    @DisplayName("Should return method not found for unknown method")
    void testUnknownMethod_ReturnsMethodNotFound() {
        McpRequest request = buildRequest(9, "unknown/method", mapper.createObjectNode());

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        assertNotNull(response.get().getError());
        assertEquals(-32601, response.get().getError().getCode());
    }

    @Test
    @DisplayName("Should handle ping with empty result")
    void testPing_ReturnsEmptyResult() throws Exception {
        McpRequest request = buildRequest(10, "ping", null);

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        assertNull(response.get().getError());
    }

    @Test
    @DisplayName("Should handle prompts/list and return prompts array")
    void testPromptsList_ReturnsArray() throws Exception {
        McpRequest request = buildRequest(11, "prompts/list", mapper.createObjectNode());

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        JsonNode resultNode = mapper.valueToTree(response.get().getResult());
        assertTrue(resultNode.has("prompts"));
        assertTrue(resultNode.get("prompts").isArray());
    }

    @Test
    @DisplayName("Should handle failed RunResult as MCP error content")
    void testToolsCall_WithFailedResult_ReturnsIsErrorTrue() throws Exception {
        stubFacade.setToolResult(RunResult.failed("Tool execution failed: timeout"));

        ObjectNode params = mapper.createObjectNode();
        params.put("name", "tool.failing.tool");
        params.set("arguments", mapper.createObjectNode());

        McpRequest request = buildRequest(12, "tools/call", params);

        Optional<McpResponse> response = handler.handle(request);

        assertTrue(response.isPresent());
        assertNull(response.get().getError()); // No JSON-RPC error, but isError in result

        JsonNode resultNode = mapper.valueToTree(response.get().getResult());
        assertTrue(resultNode.get("isError").asBoolean());
        String errorText = resultNode.get("content").get(0).get("text").asText();
        assertTrue(errorText.contains("timeout"));
    }

    // ── Helpers ──

    private McpRequest buildRequest(Object id, String method, JsonNode params) {
        return McpRequest.builder()
                .jsonrpc("2.0")
                .id(id)
                .method(method)
                .params(params)
                .build();
    }

    /**
     * Minimal stub for ArtifactRepository that returns empty lists.
     * Uses a temp directory to avoid NPE in parent constructor.
     */
    private static class StubArtifactRepository extends ArtifactRepository {
        StubArtifactRepository() {
            super(Path.of(System.getProperty("java.io.tmpdir"), "hubbers-test-empty-repo"));
        }

        @Override
        public List<String> listTools() { return Collections.emptyList(); }

        @Override
        public List<String> listPipelines() { return Collections.emptyList(); }

        @Override
        public List<String> listAgents() { return Collections.emptyList(); }

        @Override
        public List<String> listSkills() { return Collections.emptyList(); }
    }

    /**
     * Stub RuntimeFacade that captures calls and returns preset results.
     */
    private static class StubRuntimeFacade extends RuntimeFacade {
        private RunResult toolResult;
        private RunResult pipelineResult;
        private RunResult agentResult;
        private RunResult skillResult;
        private String lastToolName;
        private String lastPipelineName;
        private String lastAgentName;
        private String lastSkillName;

        StubRuntimeFacade() {
            super(null, null, null, null, null, null, null, null);
        }

        void setToolResult(RunResult result) { this.toolResult = result; }
        void setPipelineResult(RunResult result) { this.pipelineResult = result; }
        void setAgentResult(RunResult result) { this.agentResult = result; }
        void setSkillResult(RunResult result) { this.skillResult = result; }
        String getLastToolName() { return lastToolName; }
        String getLastPipelineName() { return lastPipelineName; }
        String getLastAgentName() { return lastAgentName; }
        String getLastSkillName() { return lastSkillName; }

        @Override
        public RunResult runTool(String name, JsonNode input) {
            this.lastToolName = name;
            return toolResult != null ? toolResult : RunResult.success(null);
        }

        @Override
        public RunResult runPipeline(String name, JsonNode input) {
            this.lastPipelineName = name;
            return pipelineResult != null ? pipelineResult : RunResult.success(null);
        }

        @Override
        public RunResult runAgent(String name, JsonNode input) {
            this.lastAgentName = name;
            return agentResult != null ? agentResult : RunResult.success(null);
        }

        @Override
        public RunResult runAgent(String name, JsonNode input, String conversationId) {
            this.lastAgentName = name;
            return agentResult != null ? agentResult : RunResult.success(null);
        }

        @Override
        public RunResult runSkill(String name, JsonNode input) {
            this.lastSkillName = name;
            return skillResult != null ? skillResult : RunResult.success(null);
        }
    }
}
