package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.config.LlamaCppConfig;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Model provider for llama.cpp server ({@code llama-server} / {@code llama-cpp-python}).
 *
 * <p>llama.cpp exposes an OpenAI-compatible {@code /v1/chat/completions} endpoint,
 * so this provider follows the OpenAI request/response format. Tool/function calling
 * is supported when the server is started with a grammar or a model that supports it
 * (e.g., functionary, Hermes-2, Qwen).</p>
 *
 * <p>Configuration example in {@code hubbers-config.yaml}:</p>
 * <pre>{@code
 * llamaCpp:
 *   baseUrl: http://localhost:8080
 *   defaultModel: default
 *   timeoutSeconds: 300
 * }</pre>
 *
 * @since 0.1.0
 */
@Slf4j
public class LlamaCppModelProvider implements ModelProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String DEFAULT_MODEL = "default";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final LlamaCppConfig config;
    private final Duration requestTimeout;

    /**
     * Create a provider with default Jackson mapper.
     *
     * @param httpClient shared HTTP client
     * @param config llama.cpp configuration
     */
    public LlamaCppModelProvider(HttpClient httpClient, LlamaCppConfig config) {
        this(httpClient, config, org.hubbers.util.JacksonFactory.jsonMapper());
    }

    /**
     * Create a provider with a custom ObjectMapper.
     *
     * @param httpClient shared HTTP client
     * @param config llama.cpp configuration
     * @param mapper Jackson ObjectMapper
     */
    public LlamaCppModelProvider(HttpClient httpClient, LlamaCppConfig config, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.config = config != null ? config : new LlamaCppConfig();
        this.mapper = mapper;
        this.requestTimeout = Duration.ofSeconds(
                this.config.getTimeoutSeconds() != null ? this.config.getTimeoutSeconds() : 300);
    }

    @Override
    public String providerName() {
        return "llama-cpp";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        long start = System.currentTimeMillis();
        String model = resolveModel(request);
        String baseUrl = resolveBaseUrl();
        String url = baseUrl + "/v1/chat/completions";

        log.debug("llama.cpp request to model '{}' at {}", model, url);

        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("stream", false);

            if (request.getTemperature() != null) {
                payload.put("temperature", request.getTemperature());
            }

            // Only force JSON response format when no tools are defined
            if (request.getFunctions() == null || request.getFunctions().isEmpty()) {
                payload.putObject("response_format").put("type", "json_object");
            }

            ArrayNode messages = payload.putArray("messages");
            appendMessages(messages, request);
            appendTools(payload, request);

            log.debug("llama.cpp request payload: {}", payload.toPrettyString());

            HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, mapper)
                    .post(url)
                    .timeout(requestTimeout);

            // Add API key header if configured (llama-server --api-key)
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.getApiKey());
            }

            JsonNode response = builder
                    .body(payload)
                    .executeForJson();

            return parseResponse(response, model, start);
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("llama.cpp call failed after {}ms: {}", elapsed, e.getMessage(), e);
            throw new IllegalStateException("llama.cpp call failed: " + e.getMessage(), e);
        }
    }

    private ModelResponse parseResponse(JsonNode response, String model, long start) {
        JsonNode choice = response.path("choices").path(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").isNull() ? "" : message.path("content").asText();

        ModelResponse modelResponse = new ModelResponse();
        modelResponse.setContent(content);
        modelResponse.setModel(response.path("model").asText(model));
        modelResponse.setLatencyMs(System.currentTimeMillis() - start);

        // Parse tool calls (OpenAI format)
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            modelResponse.setFunctionCalls(parseFunctionCalls(toolCallsNode));
            modelResponse.setFinishReason("function_call");
        } else {
            String finishReason = choice.path("finish_reason").asText("stop");
            modelResponse.setFinishReason(
                    "tool_calls".equals(finishReason) ? "function_call" : finishReason);
        }

        log.debug("llama.cpp response: latencyMs={}, finishReason={}, contentLength={}",
                modelResponse.getLatencyMs(), modelResponse.getFinishReason(), content.length());
        return modelResponse;
    }

    private void appendMessages(ArrayNode messages, ModelRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (Message msg : request.getMessages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.getRole());
                msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");

                if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
                    msgNode.put("tool_call_id", msg.getToolCallId());
                }
                if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    ArrayNode toolCalls = msgNode.putArray("tool_calls");
                    for (FunctionCall fc : msg.getToolCalls()) {
                        ObjectNode tcNode = toolCalls.addObject();
                        tcNode.put("id", fc.getId());
                        tcNode.put("type", "function");
                        ObjectNode fnNode = tcNode.putObject("function");
                        fnNode.put("name", fc.getName());
                        fnNode.put("arguments", serializeArguments(fc.getArguments()));
                    }
                }
            }
            return;
        }

        // Single-turn mode
        if (request.getSystemPrompt() != null) {
            messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
        }
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
            tool.put("type", "function");
            ObjectNode function = tool.putObject("function");
            function.put("name", func.getName());
            function.put("description", func.getDescription());
            if (func.getParameters() != null) {
                function.set("parameters", func.getParameters());
            }
        }
        log.debug("Added {} tool definitions to llama.cpp request", request.getFunctions().size());
    }

    private String serializeArguments(JsonNode arguments) {
        try {
            return mapper.writeValueAsString(arguments != null ? arguments : mapper.createObjectNode());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize function call arguments", e);
        }
    }

    private List<FunctionCall> parseFunctionCalls(JsonNode toolCallsNode) {
        List<FunctionCall> calls = new ArrayList<>();
        for (JsonNode tcNode : toolCallsNode) {
            JsonNode fnNode = tcNode.path("function");
            JsonNode argsNode = fnNode.path("arguments");
            JsonNode arguments;

            if (argsNode.isTextual()) {
                try {
                    arguments = mapper.readTree(argsNode.asText());
                } catch (IOException e) {
                    log.warn("Failed to parse function arguments as JSON, using empty object: {}", e.getMessage());
                    arguments = mapper.createObjectNode();
                }
            } else if (argsNode.isObject()) {
                arguments = argsNode;
            } else {
                arguments = mapper.createObjectNode();
            }

            calls.add(new FunctionCall(
                    tcNode.path("id").asText(),
                    fnNode.path("name").asText(),
                    arguments
            ));
        }
        return calls;
    }

    private String resolveBaseUrl() {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            return config.getBaseUrl().replaceAll("/$", "");
        }
        return DEFAULT_BASE_URL;
    }

    private String resolveModel(ModelRequest request) {
        if (request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel();
        }
        if (config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            return config.getDefaultModel();
        }
        return DEFAULT_MODEL;
    }
}
