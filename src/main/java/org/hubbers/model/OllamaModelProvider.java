package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.config.OllamaConfig;
import org.hubbers.util.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaModelProvider implements ModelProvider {
    private static final Logger log = LoggerFactory.getLogger(OllamaModelProvider.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2:3b";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120); // 2 minutes

    private final HttpClient httpClient;
    private final OllamaConfig config;
    private final ObjectMapper mapper = JacksonFactory.jsonMapper();

    public OllamaModelProvider(HttpClient httpClient, OllamaConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        long start = System.currentTimeMillis();
        String model = resolveModel(request);
        String baseUrl = resolveBaseUrl();
        
        log.debug("Starting Ollama API call: baseUrl={}, model={}", baseUrl, model);
        
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("stream", false);
            payload.put("format", "json");

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
            messages.addObject().put("role", "user").put("content", request.getUserPrompt());

            if (request.getTemperature() != null) {
                payload.putObject("options").put("temperature", request.getTemperature());
            }
            
            log.debug("Ollama request payload: {}", payload.toPrettyString());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)  // Add timeout to prevent infinite waiting
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            
            log.debug("Sending HTTP request to Ollama (timeout={}s)...", REQUEST_TIMEOUT.getSeconds());
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Ollama HTTP response received: statusCode={}, elapsedMs={}", response.statusCode(), elapsed);
            
            if (response.statusCode() >= 400) {
                log.error("Ollama error {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("Ollama error " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            String content = root.path("message").path("content").asText();
            
            log.debug("Ollama response parsed: contentLength={}", content.length());
            log.trace("Ollama response content: {}", content);

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(root.path("model").asText(model));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);
            
            log.debug("Ollama call completed successfully: latencyMs={}", modelResponse.getLatencyMs());
            return modelResponse;
        } catch (java.net.http.HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Ollama request timed out after {}ms (timeout={}s)", elapsed, REQUEST_TIMEOUT.getSeconds());
            throw new IllegalStateException("Ollama request timed out after " + REQUEST_TIMEOUT.getSeconds() + "s", e);
        } catch (IOException e) {
            log.error("Ollama call failed with IOException: {}", e.getMessage(), e);
            throw new IllegalStateException("Ollama call failed", e);
        } catch (InterruptedException e) {
            log.error("Ollama call interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama call interrupted", e);
        }
    }

    private String resolveBaseUrl() {
        if (config == null || config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return config.getBaseUrl();
    }

    private String resolveModel(ModelRequest request) {
        if (request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel();
        }
        if (config != null && config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            return config.getDefaultModel();
        }
        return DEFAULT_MODEL;
    }
}
