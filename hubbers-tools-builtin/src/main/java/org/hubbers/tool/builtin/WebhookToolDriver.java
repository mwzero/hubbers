package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.tool.ToolDriver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tool driver that opens a temporary HTTP listener and waits for an incoming POST request.
 *
 * <p>Useful for receiving webhook callbacks from external services as part of a pipeline.
 * The listener opens on the specified port and path, waits for a single POST, captures
 * the request body, then shuts down.</p>
 *
 * <p>Input fields:
 * <ul>
 *   <li>{@code port} — the port to listen on (required)</li>
 *   <li>{@code path} — the URL path to listen on (default: {@code /webhook})</li>
 *   <li>{@code timeout_seconds} — max time to wait for a request (default: 60)</li>
 * </ul>
 *
 * <p>Output: {@code { "received": true, "body": {...}, "content_type": "..." }}
 */
@Slf4j
public class WebhookToolDriver implements ToolDriver {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final ObjectMapper mapper;

    public WebhookToolDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "http.webhook";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        int port = input.has("port") ? input.get("port").asInt() : 0;
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port + ". Must be between 1 and 65535.");
        }

        String path = input.has("path") ? input.get("path").asText("/webhook") : "/webhook";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        int timeoutSeconds = input.has("timeout_seconds")
                ? input.get("timeout_seconds").asInt(DEFAULT_TIMEOUT_SECONDS)
                : DEFAULT_TIMEOUT_SECONDS;

        log.info("Starting webhook listener on port {} path '{}' (timeout: {}s)", port, path, timeoutSeconds);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> contentTypeRef = new AtomicReference<>();

        // Use JDK's built-in HttpServer (no external deps)
        com.sun.net.httpserver.HttpServer server;
        try {
            server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start webhook listener on port " + port + ": " + e.getMessage(), e);
        }

        String finalPath = path;
        server.createContext(path, exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String response = "Method not allowed. Send a POST request.";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                bodyRef.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            contentTypeRef.set(exchange.getRequestHeaders().getFirst("Content-Type"));

            String ack = "{\"status\":\"received\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ack.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ack.getBytes(StandardCharsets.UTF_8));
            }
            latch.countDown();
        });

        server.start();
        log.info("Webhook listener started — waiting for POST on http://localhost:{}{}", port, finalPath);

        try {
            boolean received = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!received) {
                log.warn("Webhook listener timed out after {}s on port {}", timeoutSeconds, port);
                ObjectNode output = mapper.createObjectNode();
                output.put("received", false);
                output.put("error", "Timeout: no POST received within " + timeoutSeconds + " seconds");
                return output;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook listener interrupted", e);
        } finally {
            server.stop(1);
            log.info("Webhook listener stopped on port {}", port);
        }

        // Parse body as JSON if possible
        ObjectNode output = mapper.createObjectNode();
        output.put("received", true);
        output.put("content_type", contentTypeRef.get());
        String rawBody = bodyRef.get();
        try {
            JsonNode parsedBody = mapper.readTree(rawBody);
            output.set("body", parsedBody);
        } catch (Exception e) {
            output.put("body", rawBody);
        }
        return output;
    }
}
