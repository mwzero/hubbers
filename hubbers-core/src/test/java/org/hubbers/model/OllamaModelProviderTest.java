package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hubbers.config.OllamaConfig;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaModelProviderTest {

    private final ObjectMapper mapper = JacksonFactory.jsonMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsRequestAndResponseCorrectly() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"model\":\"llama3.2:3b\",\"message\":{\"content\":\"{\\\"items\\\":[]}\"}}");
        });
        server.start();

        OllamaConfig config = new OllamaConfig();
        config.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        config.setDefaultModel("llama3.2:3b");

        OllamaModelProvider provider = new OllamaModelProvider(HttpClient.newHttpClient(), config);
        ModelRequest request = new ModelRequest();
        request.setSystemPrompt("system");
        request.setUserPrompt("{\"input\":true}");
        request.setTemperature(0.2);

        ModelResponse response = provider.generate(request);

        assertEquals("llama3.2:3b", response.getModel());
        assertEquals("{\"items\":[]}", response.getContent());
        assertTrue(response.getLatencyMs() >= 0);

        JsonNode payload = mapper.readTree(requestBody.get());
        assertEquals("llama3.2:3b", payload.path("model").asText());
        assertEquals("json", payload.path("format").asText());
        assertEquals(0.2, payload.path("options").path("temperature").asDouble(), 0.0001);
        assertNotNull(payload.path("messages"));
    }

    @Test
    void throwsOnHttpError() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat", exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));
        server.start();

        OllamaConfig config = new OllamaConfig();
        config.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        config.setDefaultModel("llama3.2:3b");

        OllamaModelProvider provider = new OllamaModelProvider(HttpClient.newHttpClient(), config);
        ModelRequest request = new ModelRequest();
        request.setSystemPrompt("system");
        request.setUserPrompt("user");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> provider.generate(request));
        assertTrue(ex.getMessage().contains("Ollama error 500"));
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
