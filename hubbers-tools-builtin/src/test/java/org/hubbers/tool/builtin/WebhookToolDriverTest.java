package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebhookToolDriver}.
 */
class WebhookToolDriverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private WebhookToolDriver driver;

    @BeforeEach
    void setUp() {
        driver = new WebhookToolDriver(mapper);
    }

    @Test
    void testType_ReturnsHttpWebhook() {
        assertEquals("http.webhook", driver.type());
    }

    @Test
    void testExecute_InvalidPort_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("port", -1);

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_PortZero_ThrowsException() {
        ObjectNode input = mapper.createObjectNode();
        input.put("port", 0);

        assertThrows(IllegalArgumentException.class,
                () -> driver.execute(null, input));
    }

    @Test
    void testExecute_ReceivesPostRequest_ReturnsBody() throws Exception {
        int port = 18923; // use a high port unlikely to be in use
        ObjectNode input = mapper.createObjectNode();
        input.put("port", port);
        input.put("path", "/test-hook");
        input.put("timeout_seconds", 10);

        // Run webhook in background, send POST after short delay
        CompletableFuture<JsonNode> resultFuture = CompletableFuture.supplyAsync(
                () -> driver.execute(null, input));

        // Give server time to start
        Thread.sleep(500);

        // Send a POST
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/test-hook"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"event\":\"test\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        // Get result
        JsonNode result = resultFuture.get(10, TimeUnit.SECONDS);
        assertTrue(result.get("received").asBoolean());
        assertEquals("application/json", result.get("content_type").asText());
        assertEquals("test", result.get("body").get("event").asText());
    }

    @Test
    void testExecute_Timeout_ReturnsFalse() {
        int port = 18924;
        ObjectNode input = mapper.createObjectNode();
        input.put("port", port);
        input.put("timeout_seconds", 1);

        JsonNode result = driver.execute(null, input);

        assertFalse(result.get("received").asBoolean());
        assertTrue(result.has("error"));
    }
}
