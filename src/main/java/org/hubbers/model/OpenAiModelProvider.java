package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.config.OpenAiConfig;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenAiModelProvider implements ModelProvider {
    private final HttpClient httpClient;
    private final OpenAiConfig config;
    private final ObjectMapper mapper = JacksonFactory.jsonMapper();

    public OpenAiModelProvider(HttpClient httpClient, OpenAiConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is missing");
        }
        long start = System.currentTimeMillis();
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", request.getModel() != null ? request.getModel() : config.getDefaultModel());
            if (request.getTemperature() != null) {
                payload.put("temperature", request.getTemperature());
            }
            payload.putObject("response_format").put("type", "json_object");
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
            messages.addObject().put("role", "user").put("content", request.getUserPrompt());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI error " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(root.path("model").asText(payload.path("model").asText()));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);
            return modelResponse;
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI call failed", e);
        }
    }
}
