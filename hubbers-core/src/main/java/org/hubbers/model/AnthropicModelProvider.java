package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.config.AnthropicConfig;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Model provider implementation for the Anthropic Messages API.
 *
 * <p>Supports Claude models with function/tool calling via the {@code tool_use} format.
 * Authentication uses the {@code x-api-key} header.</p>
 *
 * @since 0.1.0
 */
@Slf4j
public class AnthropicModelProvider implements ModelProvider {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final AnthropicConfig config;

    public AnthropicModelProvider(HttpClient httpClient, AnthropicConfig config) {
        this(httpClient, config, org.hubbers.util.JacksonFactory.jsonMapper());
    }

    public AnthropicModelProvider(HttpClient httpClient, AnthropicConfig config, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.config = config;
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Anthropic API key is missing");
        }

        long start = System.currentTimeMillis();
        String model = request.getModel() != null ? request.getModel() : config.getDefaultModel();
        String url = config.getBaseUrl().replaceAll("/$", "") + "/v1/messages";

        log.debug("Anthropic request to model '{}' at {}", model, url);

        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("max_tokens", config.getMaxTokens());

            if (request.getTemperature() != null) {
                payload.put("temperature", request.getTemperature());
            }

            // Anthropic uses a top-level "system" field, not a system message
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                payload.put("system", request.getSystemPrompt());
            } else if (request.getMessages() != null) {
                // Extract system message from message list if present
                request.getMessages().stream()
                        .filter(m -> "system".equals(m.getRole()))
                        .findFirst()
                        .ifPresent(m -> payload.put("system", m.getContent()));
            }

            ArrayNode messages = payload.putArray("messages");
            appendMessages(messages, request);
            appendTools(payload, request);

            JsonNode response = new HttpRequestBuilder(httpClient, mapper)
                    .post(url)
                    .header("x-api-key", config.getApiKey())
                    .header("anthropic-version", config.getApiVersion())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .body(payload)
                    .executeForJson();

            return parseResponse(response, model, start);
        } catch (IOException e) {
            throw new IllegalStateException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    private ModelResponse parseResponse(JsonNode response, String model, long start) {
        ModelResponse modelResponse = new ModelResponse();
        modelResponse.setModel(response.path("model").asText(model));
        modelResponse.setLatencyMs(System.currentTimeMillis() - start);

        JsonNode contentArray = response.path("content");
        StringBuilder textContent = new StringBuilder();
        List<FunctionCall> functionCalls = new ArrayList<>();

        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String type = block.path("type").asText();
                switch (type) {
                    case "text" -> textContent.append(block.path("text").asText());
                    case "tool_use" -> {
                        FunctionCall fc = new FunctionCall(
                                block.path("id").asText(),
                                block.path("name").asText(),
                                block.path("input")
                        );
                        functionCalls.add(fc);
                    }
                    default -> log.debug("Unknown Anthropic content block type: {}", type);
                }
            }
        }

        modelResponse.setContent(textContent.toString());
        String stopReason = response.path("stop_reason").asText("end_turn");

        if (!functionCalls.isEmpty()) {
            modelResponse.setFunctionCalls(functionCalls);
            modelResponse.setFinishReason("function_call");
        } else {
            modelResponse.setFinishReason("end_turn".equals(stopReason) ? "stop" : stopReason);
        }

        return modelResponse;
    }

    private void appendMessages(ArrayNode messages, ModelRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (Message msg : request.getMessages()) {
                // Skip system messages — handled as top-level "system" field
                if ("system".equals(msg.getRole())) {
                    continue;
                }

                ObjectNode msgNode = messages.addObject();

                if ("tool".equals(msg.getRole())) {
                    // Anthropic tool results use role "user" with tool_result content block
                    msgNode.put("role", "user");
                    ArrayNode content = msgNode.putArray("content");
                    ObjectNode toolResult = content.addObject();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", msg.getToolCallId());
                    toolResult.put("content", msg.getContent() != null ? msg.getContent() : "");
                } else if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    // Assistant with tool_use calls
                    msgNode.put("role", "assistant");
                    ArrayNode content = msgNode.putArray("content");
                    if (msg.getContent() != null && !msg.getContent().isBlank()) {
                        content.addObject().put("type", "text").put("text", msg.getContent());
                    }
                    for (FunctionCall fc : msg.getToolCalls()) {
                        ObjectNode toolUse = content.addObject();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", fc.getId());
                        toolUse.put("name", fc.getName());
                        toolUse.set("input", fc.getArguments() != null ? fc.getArguments() : mapper.createObjectNode());
                    }
                } else {
                    msgNode.put("role", msg.getRole());
                    msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");
                }
            }
            return;
        }

        // Single-turn mode
        if (request.getUserPrompt() != null) {
            messages.addObject().put("role", "user").put("content", request.getUserPrompt());
        }
    }

    private void appendTools(ObjectNode payload, ModelRequest request) {
        if (request.getFunctions() == null || request.getFunctions().isEmpty()) {
            return;
        }
        ArrayNode tools = payload.putArray("tools");
        for (FunctionDefinition func : request.getFunctions()) {
            ObjectNode tool = tools.addObject();
            tool.put("name", func.getName());
            tool.put("description", func.getDescription());
            if (func.getParameters() != null) {
                tool.set("input_schema", func.getParameters());
            } else {
                tool.putObject("input_schema").put("type", "object");
            }
        }
    }
}
