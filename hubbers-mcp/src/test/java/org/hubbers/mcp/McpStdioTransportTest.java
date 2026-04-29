package org.hubbers.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.mcp.protocol.McpRequest;
import org.hubbers.mcp.protocol.McpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpStdioTransport Tests")
class McpStdioTransportTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should process a valid JSON-RPC request from stdin and write response to stdout")
    void testProcessLine_WithValidRequest_WritesResponse() throws Exception {
        // Given — a "ping" request as a single line on stdin
        String request = mapper.writeValueAsString(McpRequest.builder()
                .jsonrpc("2.0").id(1).method("ping").build());
        var stdin = new ByteArrayInputStream((request + "\n").getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        // Stub handler that returns a fixed response for ping
        var handler = new StubMcpRequestHandler();
        var transport = new McpStdioTransport(handler, mapper, stdin, stdout);

        // When
        transport.run();

        // Then
        String output = stdout.toString(StandardCharsets.UTF_8).trim();
        assertFalse(output.isEmpty(), "stdout should contain the response");
        var response = mapper.readTree(output);
        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(1, response.get("id").asInt());
    }

    @Test
    @DisplayName("Should skip empty lines from stdin")
    void testProcessLine_WithEmptyLines_SkipsThem() throws Exception {
        String request = mapper.writeValueAsString(McpRequest.builder()
                .jsonrpc("2.0").id(1).method("ping").build());
        // Multiple empty lines + one valid request
        var stdin = new ByteArrayInputStream(("\n\n" + request + "\n\n").getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        var handler = new StubMcpRequestHandler();
        var transport = new McpStdioTransport(handler, mapper, stdin, stdout);

        transport.run();

        String output = stdout.toString(StandardCharsets.UTF_8).trim();
        // Should have exactly one response
        long lines = output.lines().count();
        assertEquals(1, lines, "Should have exactly one response line");
    }

    @Test
    @DisplayName("Should produce no output for notifications")
    void testProcessLine_WithNotification_NoOutput() throws Exception {
        String notification = mapper.writeValueAsString(McpRequest.builder()
                .jsonrpc("2.0").method("notifications/initialized").build());
        var stdin = new ByteArrayInputStream((notification + "\n").getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        var handler = new StubMcpRequestHandler();
        var transport = new McpStdioTransport(handler, mapper, stdin, stdout);

        transport.run();

        String output = stdout.toString(StandardCharsets.UTF_8).trim();
        assertTrue(output.isEmpty(), "Notifications should produce no output");
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully with error response")
    void testProcessLine_WithInvalidJson_WritesErrorResponse() throws Exception {
        var stdin = new ByteArrayInputStream("not-json\n".getBytes(StandardCharsets.UTF_8));
        var stdout = new ByteArrayOutputStream();

        var handler = new StubMcpRequestHandler();
        var transport = new McpStdioTransport(handler, mapper, stdin, stdout);

        transport.run();

        String output = stdout.toString(StandardCharsets.UTF_8).trim();
        assertFalse(output.isEmpty(), "Should write an error response");
        var response = mapper.readTree(output);
        assertNotNull(response.get("error"), "Should have an error field");
        assertEquals(-32700, response.get("error").get("code").asInt());
    }

    /**
     * Stub handler that mimics the real McpRequestHandler behavior for testing.
     */
    private static class StubMcpRequestHandler extends McpRequestHandler {
        StubMcpRequestHandler() {
            super(null, null, null, new ObjectMapper());
        }

        @Override
        public Optional<McpResponse> handle(McpRequest request) {
            return switch (request.getMethod()) {
                case "ping" -> Optional.of(McpResponse.success(request.getId(), java.util.Map.of()));
                case "notifications/initialized" -> Optional.empty();
                default -> Optional.of(McpResponse.methodNotFound(request.getId(), request.getMethod()));
            };
        }
    }
}
