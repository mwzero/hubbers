package org.hubbers.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.mcp.McpToolProvider;
import org.hubbers.mcp.protocol.McpToolInfo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI-compatible {@code /v1/chat/completions} proxy that exposes Hubbers artifacts
 * as function definitions. External chatbot clients (Open WebUI, llama.cpp,
 * LM Studio, text-generation-webui, Chatbox, etc.) can invoke Hubbers tools,
 * pipelines, agents, and skills via standard OpenAI function calling.
 *
 * <p>This is a <em>tool-only</em> proxy: the client brings its own LLM and decides
 * which tools to call. When the client sends a function call, Hubbers executes
 * the artifact and returns the result in OpenAI format.</p>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /v1/models} — list a virtual model with Hubbers tools</li>
 *   <li>{@code POST /v1/chat/completions} — handle chat completions with tool calls</li>
 * </ul>
 *
 * @since 0.1.0
 */
@Slf4j
public class OpenAiCompatibleProxy {

    private static final String VIRTUAL_MODEL = "hubbers-tools";

    private final RuntimeFacade runtimeFacade;
    private final McpToolProvider mcpToolProvider;
    private final ObjectMapper mapper;

    /**
     * Creates a new proxy.
     *
     * @param runtimeFacade   executes Hubbers artifacts
     * @param mcpToolProvider provides tool definitions from the artifact catalog
     * @param mapper          Jackson mapper for JSON serialization
     */
    public OpenAiCompatibleProxy(RuntimeFacade runtimeFacade,
                                 McpToolProvider mcpToolProvider,
                                 ObjectMapper mapper) {
        this.runtimeFacade = runtimeFacade;
        this.mcpToolProvider = mcpToolProvider;
        this.mapper = mapper;
    }

    /**
     * Handles {@code GET /v1/models} — returns a virtual model entry.
     *
     * @param ctx Javalin context
     */
    public void handleListModels(Context ctx) {
        ObjectNode model = mapper.createObjectNode();
        model.put("id", VIRTUAL_MODEL);
        model.put("object", "model");
        model.put("created", Instant.now().getEpochSecond());
        model.put("owned_by", "hubbers");

        ObjectNode response = mapper.createObjectNode();
        response.put("object", "list");
        ArrayNode data = response.putArray("data");
        data.add(model);

        ctx.contentType("application/json").result(response.toString());
    }

    /**
     * Handles {@code POST /v1/chat/completions}.
     *
     * <p>If the request contains messages with {@code tool_calls}, the proxy
     * executes the corresponding Hubbers artifacts and returns tool results.
     * Otherwise, it returns available tools as an assistant message listing
     * capabilities.</p>
     *
     * @param ctx Javalin context
     */
    public void handleChatCompletions(Context ctx) {
        try {
            JsonNode body = mapper.readTree(ctx.body());
            JsonNode messages = body.get("messages");

            if (messages == null || !messages.isArray() || messages.isEmpty()) {
                ctx.status(400).json(errorResponse("messages array is required"));
                return;
            }

            // Check if the last assistant message has tool_calls that need execution
            JsonNode lastMessage = messages.get(messages.size() - 1);
            String role = lastMessage.has("role") ? lastMessage.get("role").asText() : "";

            if ("tool".equals(role)) {
                // Client is sending back a tool result — acknowledge
                ctx.contentType("application/json").result(
                    mapper.writeValueAsString(buildAssistantResponse(
                        "Tool result received.", body))
                );
                return;
            }

            // Check if any message contains tool_calls for us to execute
            for (int i = messages.size() - 1; i >= 0; i--) {
                JsonNode msg = messages.get(i);
                if (msg.has("tool_calls") && msg.get("tool_calls").isArray()) {
                    ObjectNode response = executeToolCalls(msg.get("tool_calls"), body);
                    ctx.contentType("application/json").result(mapper.writeValueAsString(response));
                    return;
                }
            }

            // Normal chat message — return assistant response with available tools
            List<McpToolInfo> tools = mcpToolProvider.listTools();
            ObjectNode response = buildAssistantResponseWithTools(messages, tools, body);
            ctx.contentType("application/json").result(mapper.writeValueAsString(response));

        } catch (Exception e) {
            log.error("OpenAI proxy error: {}", e.getMessage(), e);
            ctx.status(500).json(errorResponse(e.getMessage()));
        }
    }

    /**
     * Returns the list of all Hubbers artifacts as OpenAI function tool definitions.
     * Intended to be appended to client requests.
     *
     * @return the tools array as a JsonNode
     */
    public ArrayNode getToolDefinitions() {
        ArrayNode toolsArray = mapper.createArrayNode();
        for (McpToolInfo tool : mcpToolProvider.listTools()) {
            ObjectNode toolDef = mapper.createObjectNode();
            toolDef.put("type", "function");

            ObjectNode function = mapper.createObjectNode();
            function.put("name", sanitizeName(tool.getName()));
            function.put("description", tool.getDescription());
            function.set("parameters", tool.getInputSchema());

            toolDef.set("function", function);
            toolsArray.add(toolDef);
        }
        return toolsArray;
    }

    /**
     * Executes tool calls from an assistant message and returns the results
     * as a chat completions response with tool messages.
     */
    private ObjectNode executeToolCalls(JsonNode toolCalls, JsonNode originalRequest) {
        ArrayNode resultMessages = mapper.createArrayNode();

        for (JsonNode toolCall : toolCalls) {
            String callId = toolCall.has("id") ? toolCall.get("id").asText() : UUID.randomUUID().toString();
            JsonNode function = toolCall.get("function");
            String functionName = function.get("name").asText();

            JsonNode arguments;
            try {
                String argsStr = function.has("arguments") ? function.get("arguments").asText() : "{}";
                arguments = mapper.readTree(argsStr);
            } catch (Exception e) {
                arguments = mapper.createObjectNode();
            }

            log.info("OpenAI proxy executing: {} with call_id={}", functionName, callId);

            String artifactName = unsanitizeName(functionName);
            try {
                RunResult result = routeAndExecute(artifactName, arguments);
                String content = result.getStatus() == ExecutionStatus.SUCCESS
                        ? result.getOutput() != null ? result.getOutput().toString() : "{}"
                        : "Error: " + (result.getError() != null ? result.getError() : "Execution failed");

                ObjectNode toolMsg = mapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", callId);
                toolMsg.put("content", content);
                resultMessages.add(toolMsg);
            } catch (Exception e) {
                ObjectNode toolMsg = mapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", callId);
                toolMsg.put("content", "Error: " + e.getMessage());
                resultMessages.add(toolMsg);
            }
        }

        return buildToolResultResponse(resultMessages, originalRequest);
    }

    /**
     * Routes a function name to the appropriate Hubbers executor.
     * Reuses the same prefix convention as MCP: tool.*, pipeline.*, agent.*, skill.*
     */
    private RunResult routeAndExecute(String fullName, JsonNode arguments) {
        if (fullName.startsWith("tool.")) {
            return runtimeFacade.runTool(fullName.substring("tool.".length()), arguments);
        }
        if (fullName.startsWith("pipeline.")) {
            return runtimeFacade.runPipeline(fullName.substring("pipeline.".length()), arguments);
        }
        if (fullName.startsWith("agent.")) {
            return runtimeFacade.runAgent(fullName.substring("agent.".length()), arguments);
        }
        if (fullName.startsWith("skill.")) {
            return runtimeFacade.runSkill(fullName.substring("skill.".length()), arguments);
        }
        throw new IllegalArgumentException("Unknown artifact: " + fullName);
    }

    /**
     * Builds a response when tool results are being returned.
     */
    private ObjectNode buildToolResultResponse(ArrayNode toolMessages, JsonNode originalRequest) {
        ObjectNode response = mapper.createObjectNode();
        response.put("id", "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8));
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", getRequestModel(originalRequest));

        ArrayNode choices = response.putArray("choices");
        ObjectNode choice = mapper.createObjectNode();
        choice.put("index", 0);

        // Aggregate tool results into assistant message
        StringBuilder content = new StringBuilder();
        for (JsonNode msg : toolMessages) {
            content.append(msg.get("content").asText()).append("\n");
        }

        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", content.toString().trim());
        choice.set("message", message);
        choice.put("finish_reason", "stop");
        choices.add(choice);

        response.set("usage", buildUsage());
        return response;
    }

    /**
     * Builds a regular assistant response that includes available tool definitions.
     */
    private ObjectNode buildAssistantResponseWithTools(JsonNode messages, List<McpToolInfo> tools,
                                                       JsonNode originalRequest) {
        // Build a summary of available tools as assistant content
        StringBuilder sb = new StringBuilder("I have access to the following Hubbers artifacts:\n\n");
        for (McpToolInfo tool : tools) {
            sb.append("- **").append(tool.getName()).append("**: ").append(tool.getDescription()).append("\n");
        }
        sb.append("\nYou can ask me to use any of these tools.");

        return buildAssistantResponse(sb.toString(), originalRequest);
    }

    private ObjectNode buildAssistantResponse(String content, JsonNode originalRequest) {
        ObjectNode response = mapper.createObjectNode();
        response.put("id", "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8));
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", getRequestModel(originalRequest));

        ArrayNode choices = response.putArray("choices");
        ObjectNode choice = mapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", content);
        choice.set("message", message);
        choice.put("finish_reason", "stop");
        choices.add(choice);

        response.set("usage", buildUsage());
        return response;
    }

    private ObjectNode buildUsage() {
        ObjectNode usage = mapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("completion_tokens", 0);
        usage.put("total_tokens", 0);
        return usage;
    }

    private String getRequestModel(JsonNode request) {
        if (request != null && request.has("model")) {
            return request.get("model").asText();
        }
        return VIRTUAL_MODEL;
    }

    private Object errorResponse(String message) {
        return new java.util.LinkedHashMap<>() {{
            put("error", new java.util.LinkedHashMap<>() {{
                put("message", message);
                put("type", "server_error");
                put("code", "internal_error");
            }});
        }};
    }

    /**
     * Converts an artifact name to a valid OpenAI function name.
     * OpenAI requires function names to match {@code [a-zA-Z0-9_-]}.
     */
    private String sanitizeName(String name) {
        return name.replace(".", "_");
    }

    /**
     * Reverses {@link #sanitizeName(String)} to recover the artifact name.
     */
    private String unsanitizeName(String functionName) {
        // MCP names use dots: tool.rss.fetch → tool_rss_fetch
        // We need to restore the first dot (prefix separator), rest stay as underscores
        // since artifact names also use dots
        int firstUnderscore = functionName.indexOf('_');
        if (firstUnderscore < 0) {
            return functionName;
        }
        String prefix = functionName.substring(0, firstUnderscore);
        String rest = functionName.substring(firstUnderscore + 1).replace("_", ".");
        return prefix + "." + rest;
    }
}
