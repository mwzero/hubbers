package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.tool.ToolExecutionException;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpToolDriverTest {

    private HttpToolDriver driver;
    private ObjectMapper mapper;
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        mapper = JacksonFactory.jsonMapper();
        HttpClient httpClient = HttpClient.newHttpClient();
        driver = new HttpToolDriver(httpClient, mapper);

        // Start a lightweight embedded HTTP server for testing
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;

        // GET endpoint
        server.createContext("/api/data", exchange -> {
            String response = "{\"status\":\"ok\",\"data\":[1,2,3]}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        server.createContext("/api/manifest", exchange -> {
            String[] segments = exchange.getRequestURI().getPath().split("/");
            String type = segments.length > 3 ? segments[3] : "";
            String name = segments.length > 4 ? segments[4] : "";
            String query = exchange.getRequestURI().getQuery();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String traceHeader = exchange.getRequestHeaders().getFirst("X-Trace-Id");

            String response = String.format(
                "{\"type\":\"%s\",\"name\":\"%s\",\"query\":\"%s\",\"authorization\":\"%s\",\"trace\":\"%s\"}",
                type,
                name,
                query != null ? query : "",
                authorization != null ? authorization : "",
                traceHeader != null ? traceHeader : ""
            );
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        // POST endpoint — echoes body
        server.createContext("/api/echo", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes());
            String response = "{\"received\":" + body + ",\"method\":\"" + exchange.getRequestMethod() + "\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        // Error endpoint
        server.createContext("/api/error", exchange -> {
            String response = "{\"error\":\"not found\"}";
            exchange.sendResponseHeaders(404, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testToolType() {
        assertEquals("http", driver.type(), "Driver type should be http");
    }

    @Test
    void testGetRequest() {
        ToolManifest manifest = createManifest("test.get", baseUrl + "/api/data", "GET");
        ObjectNode input = mapper.createObjectNode();

        JsonNode result = driver.execute(manifest, input);

        assertEquals("ok", result.path("status").asText());
        assertTrue(result.path("data").isArray());
        assertEquals(3, result.path("data").size());
    }

    @Test
    void testPostRequest() {
        ToolManifest manifest = createManifest("test.post", baseUrl + "/api/echo", "POST");
        ObjectNode input = mapper.createObjectNode();
        input.put("message", "hello");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("POST", result.path("method").asText());
        assertEquals("hello", result.path("received").path("message").asText());
    }

    @Test
    void testPutRequest() {
        ToolManifest manifest = createManifest("test.put", baseUrl + "/api/echo", "PUT");
        ObjectNode input = mapper.createObjectNode();
        input.put("update", "data");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("PUT", result.path("method").asText());
    }

    @Test
    void testDeleteRequest() {
        ToolManifest manifest = createManifest("test.delete", baseUrl + "/api/data", "DELETE");
        ObjectNode input = mapper.createObjectNode();

        JsonNode result = driver.execute(manifest, input);

        assertNotNull(result, "DELETE should return a response");
    }

    @Test
    void testMissingBaseUrl() {
        ToolManifest manifest = new ToolManifest();
        Metadata meta = new Metadata();
        meta.setName("test.nourl");
        manifest.setTool(meta);
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        manifest.setConfig(config);

        ObjectNode input = mapper.createObjectNode();

        assertThrows(ToolExecutionException.class,
            () -> driver.execute(manifest, input),
            "Should throw when base_url is missing");
    }

    @Test
    void testUnsupportedMethod() {
        ToolManifest manifest = createManifest("test.patch", baseUrl + "/api/data", "PATCH");
        ObjectNode input = mapper.createObjectNode();

        assertThrows(ToolExecutionException.class,
            () -> driver.execute(manifest, input),
            "Should throw for unsupported HTTP method");
    }

    @Test
    void testDefaultMethodIsPost() {
        ToolManifest manifest = new ToolManifest();
        Metadata meta = new Metadata();
        meta.setName("test.default");
        manifest.setTool(meta);
        Map<String, Object> config = new HashMap<>();
        config.put("base_url", baseUrl + "/api/echo");
        // No method specified — should default to POST
        manifest.setConfig(config);

        ObjectNode input = mapper.createObjectNode();
        input.put("key", "value");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("POST", result.path("method").asText(),
            "Default method should be POST");
    }

    @Test
    void testExecute_WithTemplatedBrunoConfig_ResolvesUrlHeadersAndQuery() {
        ToolManifest manifest = createManifest("test.bruno", "{{baseUrl}}/api/manifest/:type/:name", "GET");
        Map<String, Object> config = new HashMap<>(manifest.getConfig());
        config.put("variables", Map.of(
            "baseUrl", baseUrl,
            "apiKey", "demo-token"
        ));
        config.put("path_params", Map.of(
            "type", "agents",
            "name", "hello-world"
        ));
        config.put("query_params", Map.of(
            "expand", "{{expand}}"
        ));
        config.put("headers", Map.of(
            "Authorization", "Bearer {{apiKey}}",
            "X-Trace-Id", "{{traceId}}"
        ));
        manifest.setConfig(config);

        ObjectNode input = mapper.createObjectNode();
        input.put("name", "weather.lookup");
        input.put("expand", "true");
        input.put("traceId", "trace-123");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("agents", result.path("type").asText());
        assertEquals("weather.lookup", result.path("name").asText());
        assertEquals("expand=true", result.path("query").asText());
        assertEquals("Bearer demo-token", result.path("authorization").asText());
        assertEquals("trace-123", result.path("trace").asText());
    }

    @Test
    void testExecute_WithBodyTemplate_ResolvesRawRequestBody() {
        ToolManifest manifest = createManifest("test.body.template", baseUrl + "/api/echo", "POST");
        Map<String, Object> config = new LinkedHashMap<>(manifest.getConfig());
        config.put("body_type", "json");
        config.put("body_template", "{\n  \"name\": \"{{toolName}}\",\n  \"kind\": \"generated\"\n}");
        manifest.setConfig(config);

        ObjectNode input = mapper.createObjectNode();
        input.put("toolName", "bruno.hubbers-api.artifact-discovery.get-manifest");

        JsonNode result = driver.execute(manifest, input);

        assertEquals("POST", result.path("method").asText());
        assertEquals("bruno.hubbers-api.artifact-discovery.get-manifest",
            result.path("received").path("name").asText());
        assertEquals("generated", result.path("received").path("kind").asText());
    }

    private ToolManifest createManifest(String name, String url, String method) {
        ToolManifest manifest = new ToolManifest();
        Metadata meta = new Metadata();
        meta.setName(name);
        manifest.setTool(meta);
        Map<String, Object> config = new HashMap<>();
        config.put("base_url", url);
        config.put("method", method);
        manifest.setConfig(config);
        return manifest;
    }
}
