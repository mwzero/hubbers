package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MCP Protocol Serialization Tests")
class McpProtocolSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should deserialize JSON-RPC request with method and params")
    void testDeserializeRequest_WithParams_ParsesCorrectly() throws Exception {
        String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "tools/list",
                    "params": {}
                }
                """;

        McpRequest request = mapper.readValue(json, McpRequest.class);

        assertEquals("2.0", request.getJsonrpc());
        assertEquals(1, request.getId());
        assertEquals("tools/list", request.getMethod());
        assertNotNull(request.getParams());
    }

    @Test
    @DisplayName("Should deserialize initialize request with nested params")
    void testDeserializeRequest_Initialize_ParsesNestedParams() throws Exception {
        String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "protocolVersion": "2025-03-26",
                        "capabilities": {
                            "tools": {}
                        },
                        "clientInfo": {
                            "name": "open-webui",
                            "version": "0.6.31"
                        }
                    }
                }
                """;

        McpRequest request = mapper.readValue(json, McpRequest.class);

        assertEquals("initialize", request.getMethod());
        assertTrue(request.getParams().has("protocolVersion"));
        assertEquals("2025-03-26", request.getParams().get("protocolVersion").asText());
    }

    @Test
    @DisplayName("Should deserialize tools/call request with name and arguments")
    void testDeserializeRequest_ToolsCall_ParsesNameAndArguments() throws Exception {
        String json = """
                {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "tools/call",
                    "params": {
                        "name": "tool.web.search",
                        "arguments": {
                            "query": "Java MCP protocol",
                            "limit": 5
                        }
                    }
                }
                """;

        McpRequest request = mapper.readValue(json, McpRequest.class);

        assertEquals("tools/call", request.getMethod());
        assertEquals("tool.web.search", request.getParams().get("name").asText());
        assertEquals("Java MCP protocol", request.getParams().get("arguments").get("query").asText());
        assertEquals(5, request.getParams().get("arguments").get("limit").asInt());
    }

    @Test
    @DisplayName("Should serialize success response correctly")
    void testSerializeResponse_Success_IncludesResult() throws Exception {
        McpResponse response = McpResponse.success(1, "hello");

        String json = mapper.writeValueAsString(response);

        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"result\":\"hello\""));
        assertFalse(json.contains("\"error\""));
    }

    @Test
    @DisplayName("Should serialize error response correctly")
    void testSerializeResponse_Error_IncludesErrorObject() throws Exception {
        McpResponse response = McpResponse.error(42, -32601, "Method not found");

        String json = mapper.writeValueAsString(response);

        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":42"));
        assertTrue(json.contains("-32601"));
        assertTrue(json.contains("Method not found"));
        assertFalse(json.contains("\"result\""));
    }

    @Test
    @DisplayName("Should serialize McpToolInfo with inputSchema")
    void testSerializeToolInfo_WithSchema_SerializesCorrectly() throws Exception {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");

        McpToolInfo tool = McpToolInfo.builder()
                .name("tool.web.search")
                .description("Search the web")
                .inputSchema(schema)
                .build();

        String json = mapper.writeValueAsString(tool);

        assertTrue(json.contains("\"name\":\"tool.web.search\""));
        assertTrue(json.contains("\"description\":\"Search the web\""));
        assertTrue(json.contains("\"inputSchema\""));
    }

    @Test
    @DisplayName("Should serialize McpToolCallResult success")
    void testSerializeToolCallResult_Success_HasContentAndNoError() throws Exception {
        McpToolCallResult result = McpToolCallResult.success("{\"data\": \"test\"}");

        String json = mapper.writeValueAsString(result);

        assertTrue(json.contains("\"type\":\"text\""));
        assertTrue(json.contains("\"text\""), "Should contain text field");
        assertTrue(json.contains("data"), "Should contain the data payload");
        assertTrue(json.contains("\"isError\":false"), "Should contain isError:false, got: " + json);
    }

    @Test
    @DisplayName("Should serialize McpToolCallResult error")
    void testSerializeToolCallResult_Error_HasIsErrorTrue() throws Exception {
        McpToolCallResult result = McpToolCallResult.error("Something went wrong");

        String json = mapper.writeValueAsString(result);

        assertTrue(json.contains("\"isError\":true"));
        assertTrue(json.contains("Something went wrong"));
    }

    @Test
    @DisplayName("Should create method not found response")
    void testMethodNotFound_ContainsCorrectCode() {
        McpResponse response = McpResponse.methodNotFound(5, "unknown/method");

        assertNotNull(response.getError());
        assertEquals(-32601, response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("unknown/method"));
    }

    @Test
    @DisplayName("Should handle request with string ID")
    void testDeserializeRequest_WithStringId_ParsesCorrectly() throws Exception {
        String json = """
                {
                    "jsonrpc": "2.0",
                    "id": "abc-123",
                    "method": "ping"
                }
                """;

        McpRequest request = mapper.readValue(json, McpRequest.class);

        assertEquals("abc-123", request.getId());
        assertEquals("ping", request.getMethod());
    }

    @Test
    @DisplayName("Should handle notification without id")
    void testDeserializeRequest_Notification_HasNullId() throws Exception {
        String json = """
                {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized"
                }
                """;

        McpRequest request = mapper.readValue(json, McpRequest.class);

        assertNull(request.getId());
        assertEquals("notifications/initialized", request.getMethod());
    }
}
