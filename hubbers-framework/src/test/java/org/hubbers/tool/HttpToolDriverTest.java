package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
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
