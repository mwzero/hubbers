package org.hubbers.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.mcp.McpRequestHandler;
import org.hubbers.mcp.protocol.McpRequest;
import org.hubbers.mcp.protocol.McpResponse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP SSE (Server-Sent Events) transport following the MCP SSE specification.
 *
 * <p>Provides two Javalin-based endpoints:
 * <ul>
 *   <li>{@code GET /mcp/sse} — SSE connection endpoint. On connect, sends an
 *       {@code endpoint} event with the URL for sending messages.</li>
 *   <li>{@code POST /mcp/messages?sessionId=...} — receives JSON-RPC messages
 *       from the client and returns the response inline.</li>
 * </ul>
 *
 * <p>This transport is designed for backward compatibility with older MCP clients
 * that do not support the newer streamable HTTP transport.</p>
 *
 * @since 0.1.0
 */
@Slf4j
public class McpSseTransport {

    private final McpRequestHandler handler;
    private final ObjectMapper mapper;

    /** Active SSE sessions for tracking. */
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    /**
     * Creates a new SSE transport.
     *
     * @param handler the MCP request handler
     * @param mapper  Jackson mapper for JSON serialization
     */
    public McpSseTransport(McpRequestHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    /**
     * Handles {@code GET /mcp/sse} — establishes an SSE connection.
     * Sends the endpoint URL as the first event per MCP SSE spec.
     *
     * @param ctx Javalin HTTP context
     */
    public void handleSseConnect(Context ctx) {
        String sessionId = UUID.randomUUID().toString();
        log.info("MCP SSE client connected: session={}", sessionId);

        activeSessions.put(sessionId, sessionId);

        ctx.contentType("text/event-stream");
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");

        // Build the message endpoint URL
        String baseUrl = ctx.scheme() + "://" + ctx.host();
        String messageUrl = baseUrl + "/mcp/messages?sessionId=" + sessionId;

        // Send the endpoint event per MCP SSE spec
        ctx.result("event: endpoint\ndata: " + messageUrl + "\n\n");
    }

    /**
     * Handles {@code POST /mcp/messages?sessionId=...} — receives a JSON-RPC
     * message from the client and responds inline.
     *
     * @param ctx Javalin HTTP context
     */
    public void handleMessage(Context ctx) {
        String sessionId = ctx.queryParam("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            ctx.status(400).json(Map.of("error", "Missing sessionId parameter"));
            return;
        }

        try {
            McpRequest request = mapper.readValue(ctx.body(), McpRequest.class);
            log.debug("MCP SSE message: session={}, method={}", sessionId, request.getMethod());

            Optional<McpResponse> response = handler.handle(request);

            if (response.isPresent()) {
                ctx.contentType("application/json").result(
                    mapper.writeValueAsString(response.get()));
            } else {
                // Notification — no response body
                ctx.status(202).result("");
            }
        } catch (Exception e) {
            log.error("MCP SSE message error: session={}, error={}", sessionId, e.getMessage(), e);
            try {
                McpResponse errorResp = McpResponse.internalError(null, e.getMessage());
                ctx.contentType("application/json").result(mapper.writeValueAsString(errorResp));
            } catch (Exception ex) {
                ctx.status(500).result("Internal error");
            }
        }
    }

    /**
     * Returns the number of tracked SSE sessions.
     *
     * @return active session count
     */
    public int activeConnections() {
        return activeSessions.size();
    }
}
