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
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is missing");
        }
        
        long start = System.currentTimeMillis();
        
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", request.getModel() != null ? request.getModel() : config.getDefaultModel());
            if (request.getTemperature() != null) {
                payload.put("temperature", request.getTemperature());
            }
            if (request.getFunctions() == null || request.getFunctions().isEmpty()) {
                payload.putObject("response_format").put("type", "json_object");
            }

            ArrayNode messages = payload.putArray("messages");
            appendMessages(messages, request);
            appendTools(payload, request);

            JsonNode response = new HttpRequestBuilder(httpClient, mapper)
                    .post(config.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(Duration.ofSeconds(120))
                    .body(payload)
                    .executeForJson();

            JsonNode choice = response.path("choices").path(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").isNull() ? "" : message.path("content").asText();

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(response.path("model").asText(payload.path("model").asText()));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);

            // Extract token usage from OpenAI response
            JsonNode usageNode = response.path("usage");
            if (!usageNode.isMissingNode()) {
                modelResponse.setPromptTokens(usageNode.path("prompt_tokens").asLong(0));
                modelResponse.setCompletionTokens(usageNode.path("completion_tokens").asLong(0));
                modelResponse.setTotalTokens(usageNode.path("total_tokens").asLong(0));
            }

            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                modelResponse.setFunctionCalls(parseFunctionCalls(toolCallsNode));
                modelResponse.setFinishReason("function_call");
            } else {
                String finishReason = choice.path("finish_reason").asText("stop");
                modelResponse.setFinishReason("tool_calls".equals(finishReason) ? "function_call" : finishReason);
            }
            return modelResponse;
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI call failed: " + e.getMessage(), e);
        }
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
                    for (FunctionCall functionCall : msg.getToolCalls()) {
                        ObjectNode toolCallNode = toolCalls.addObject();
                        toolCallNode.put("id", functionCall.getId());
                        toolCallNode.put("type", "function");
                        ObjectNode functionNode = toolCallNode.putObject("function");
                        functionNode.put("name", functionCall.getName());
                        functionNode.put("arguments", serializeArguments(functionCall.getArguments()));
                    }
                }
            }
            return;
        }

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
                function.set("parameters", normalizeSchema(func.getParameters()));
            }
        }
    }

    private String serializeArguments(JsonNode arguments) {
        try {
            return mapper.writeValueAsString(arguments != null ? arguments : mapper.createObjectNode());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize function call arguments", e);
        }
    }

    private java.util.List<FunctionCall> parseFunctionCalls(JsonNode toolCallsNode) {
        java.util.List<FunctionCall> functionCalls = new java.util.ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            JsonNode functionNode = toolCallNode.path("function");
            JsonNode argumentsNode = functionNode.path("arguments");
            JsonNode arguments = argumentsNode;

            if (argumentsNode.isTextual()) {
                try {
                    arguments = mapper.readTree(argumentsNode.asText());
                } catch (IOException e) {
                    throw new IllegalStateException("OpenAI returned invalid function arguments", e);
                }
            }

            functionCalls.add(new FunctionCall(
                toolCallNode.path("id").asText(),
                functionNode.path("name").asText(),
                arguments
            ));
        }
        return functionCalls;
    }

    private JsonNode normalizeSchema(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return schema;
        }

        ObjectNode normalized = mapper.createObjectNode();
        if (schema.has("type")) {
            normalized.set("type", schema.get("type"));
        }

        JsonNode propertiesNode = schema.get("properties");
        if (propertiesNode != null && propertiesNode.isObject()) {
            ObjectNode normalizedProperties = mapper.createObjectNode();
            ArrayNode requiredFields = mapper.createArrayNode();

            propertiesNode.fields().forEachRemaining(entry -> {
                String propName = entry.getKey();
                JsonNode propValue = entry.getValue();

                if (propValue.isObject()) {
                    ObjectNode normalizedProp = mapper.createObjectNode();
                    boolean isRequired = propValue.has("required") && propValue.get("required").asBoolean(false);
                    if (isRequired) {
                        requiredFields.add(propName);
                    }

                    propValue.fields().forEachRemaining(field -> {
                        if (!"required".equals(field.getKey())) {
                            normalizedProp.set(field.getKey(), field.getValue());
                        }
                    });
                    normalizedProperties.set(propName, normalizedProp);
                } else {
                    normalizedProperties.set(propName, propValue);
                }
            });

            normalized.set("properties", normalizedProperties);
            if (requiredFields.size() > 0) {
                normalized.set("required", requiredFields);
            }
        }

        return normalized;
    }
}
