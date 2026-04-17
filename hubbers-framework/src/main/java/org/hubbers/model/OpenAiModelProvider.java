package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.config.OpenAiConfig;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Model provider implementation for OpenAI API.
 * 
 * <p>Supports GPT models with JSON response format. Uses HttpRequestBuilder
 * for clean HTTP operations and proper error handling.</p>
 * 
 * @since 0.1.0
 */
public class OpenAiModelProvider implements ModelProvider {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final OpenAiConfig config;

    public OpenAiModelProvider(HttpClient httpClient, OpenAiConfig config) {
        this(httpClient, config, org.hubbers.util.JacksonFactory.jsonMapper());
    }

    public OpenAiModelProvider(HttpClient httpClient, OpenAiConfig config, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.config = config;
        this.mapper = mapper;
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

            JsonNode response = new HttpRequestBuilder(httpClient, mapper)
                    .post(config.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(Duration.ofSeconds(120))
                    .body(payload)
                    .executeForJson();

            String content = response.path("choices").path(0).path("message").path("content").asText();

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(response.path("model").asText(payload.path("model").asText()));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);
            return modelResponse;
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI call failed: " + e.getMessage(), e);
        }
    }
}
