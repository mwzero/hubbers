package org.hubbers.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.mcp.protocol.McpRequest;
import org.hubbers.mcp.protocol.McpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * MCP stdio transport for external chat UIs (Claude Desktop, VS Code, etc.).
 *
 * <p>Reads JSON-RPC 2.0 requests line-by-line from stdin and writes
 * responses to stdout. Each message is a single line of JSON followed
 * by a newline. Diagnostic logging goes to stderr via SLF4J.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * McpStdioTransport transport = new McpStdioTransport(handler, mapper);
 * transport.run(); // blocks until stdin is closed
 * }</pre>
 *
 * @see McpRequestHandler
 * @since 0.1.0
 */
@Slf4j
public class McpStdioTransport {

    private final McpRequestHandler handler;
    private final ObjectMapper mapper;
    private final BufferedReader reader;
    private final PrintWriter writer;

    /**
     * Creates a stdio transport using System.in / System.out.
     *
     * @param handler the MCP request handler
     * @param mapper  Jackson mapper for JSON serialization
     */
    public McpStdioTransport(McpRequestHandler handler, ObjectMapper mapper) {
        this(handler, mapper, System.in, System.out);
    }

    /**
     * Creates a stdio transport with custom streams (useful for testing).
     *
     * @param handler the MCP request handler
     * @param mapper  Jackson mapper for JSON serialization
     * @param in      input stream to read JSON-RPC requests from
     * @param out     output stream to write JSON-RPC responses to
     */
    public McpStdioTransport(McpRequestHandler handler, ObjectMapper mapper,
                             InputStream in, OutputStream out) {
        this.handler = handler;
        this.mapper = mapper;
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(out, true, StandardCharsets.UTF_8);
    }

    /**
     * Starts the stdio transport loop. Blocks until the input stream is closed
     * or an unrecoverable error occurs.
     *
     * <p>Each line from stdin is parsed as a JSON-RPC 2.0 request and dispatched
     * to the {@link McpRequestHandler}. Responses are written as single-line JSON
     * to stdout. Notifications (no response) produce no output.</p>
     */
    public void run() {
        log.info("MCP stdio transport started — reading from stdin");

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    processLine(line);
                } catch (Exception e) {
                    log.error("Failed to process MCP request: {}", e.getMessage(), e);
                    writeErrorResponse(null, -32700, "Parse error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("MCP stdio transport I/O error: {}", e.getMessage(), e);
        }

        log.info("MCP stdio transport stopped — stdin closed");
    }

    /**
     * Processes a single JSON-RPC line from stdin.
     *
     * @param line the raw JSON string
     * @throws IOException if JSON parsing fails
     */
    private void processLine(String line) throws IOException {
        log.debug("MCP stdin: {}", line);

        McpRequest request = mapper.readValue(line, McpRequest.class);
        Optional<McpResponse> response = handler.handle(request);

        if (response.isPresent()) {
            String json = mapper.writeValueAsString(response.get());
            writer.println(json);
            writer.flush();
            log.debug("MCP stdout: {}", json);
        }
        // Notifications (empty response) produce no output — correct per MCP spec
    }

    /**
     * Writes a JSON-RPC error response to stdout.
     *
     * @param id      the request ID (may be null for parse errors)
     * @param code    JSON-RPC error code
     * @param message human-readable error message
     */
    private void writeErrorResponse(Object id, int code, String message) {
        try {
            McpResponse errorResponse = McpResponse.error(id, code, message);
            String json = mapper.writeValueAsString(errorResponse);
            writer.println(json);
            writer.flush();
        } catch (Exception e) {
            log.error("Failed to write error response: {}", e.getMessage(), e);
        }
    }
}
