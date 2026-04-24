package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.config.AzureOpenAiConfig;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Model provider implementation for Azure OpenAI Service.
 *
 * <p>Uses Azure-specific endpoint format with API key authentication via the
 * {@code api-key} header. Supports chat completions with function/tool calling.</p>
 *
 * @since 0.1.0
 */
@Slf4j
public class AzureOpenAiModelProvider implements ModelProvider {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final AzureOpenAiConfig config;

    public AzureOpenAiModelProvider(HttpClient httpClient, AzureOpenAiConfig config) {
        this(httpClient, config, org.hubbers.util.JacksonFactory.jsonMapper());
    }

    public AzureOpenAiModelProvider(HttpClient httpClient, AzureOpenAiConfig config, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.config = config;
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return "azure-openai";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Azure OpenAI API key is missing");
        }
        if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new IllegalStateException("Azure OpenAI endpoint is missing");
        }

        long start = System.currentTimeMillis();
        String deployment = request.getModel() != null ? request.getModel()
                : (config.getDeployment() != null ? config.getDeployment() : config.getDefaultModel());

        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                config.getEndpoint().replaceAll("/$", ""),
                deployment,
                config.getApiVersion());

        log.debug("Azure OpenAI request to deployment '{}' at {}", deployment, config.getEndpoint());

        try {
            ObjectNode payload = mapper.createObjectNode();
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
                    .post(url)
                    .header("api-key", config.getApiKey())
                    .timeout(Duration.ofSeconds(120))
                    .body(payload)
                    .executeForJson();

            JsonNode choice = response.path("choices").path(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").isNull() ? "" : message.path("content").asText();

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(deployment);
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);

            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                modelResponse.setFunctionCalls(parseFunctionCalls(toolCallsNode));
                modelResponse.setFinishReason("function_call");
            } else {
                String finishReason = choice.path("finish_reason").asText("stop");
                modelResponse.setFinishReason("tool_calls".equals(finishReason) ? "function_call" : finishReason);
            }
            return modelResponse;
        } catch (IOException e) {
            throw new IllegalStateException("Azure OpenAI call failed: " + e.getMessage(), e);
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
    }

    private String serializeArguments(JsonNode arguments) {
        try {
            return mapper.writeValueAsString(arguments != null ? arguments : mapper.createObjectNode());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize function call arguments", e);
        }
    }

    private List<FunctionCall> parseFunctionCalls(JsonNode toolCallsNode) {
        List<FunctionCall> functionCalls = new ArrayList<>();
        for (JsonNode tcNode : toolCallsNode) {
            JsonNode fnNode = tcNode.path("function");
            JsonNode argsNode = fnNode.path("arguments");
            JsonNode arguments = argsNode;
            if (argsNode.isTextual()) {
                try {
                    arguments = mapper.readTree(argsNode.asText());
                } catch (IOException e) {
                    throw new IllegalStateException("Azure OpenAI returned invalid function arguments", e);
                }
            }
            functionCalls.add(new FunctionCall(
                    tcNode.path("id").asText(),
                    fnNode.path("name").asText(),
                    arguments
            ));
        }
        return functionCalls;
    }
}
