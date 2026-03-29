package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.config.OllamaConfig;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OllamaModelProvider implements ModelProvider {
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2:3b";

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
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", resolveModel(request));
            payload.put("stream", false);
            payload.put("format", "json");

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
            messages.addObject().put("role", "user").put("content", request.getUserPrompt());

            if (request.getTemperature() != null) {
                payload.putObject("options").put("temperature", request.getTemperature());
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Ollama error " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            String content = root.path("message").path("content").asText();

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(root.path("model").asText(resolveModel(request)));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);
            return modelResponse;
        } catch (IOException e) {
            throw new IllegalStateException("Ollama call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama call failed", e);
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
