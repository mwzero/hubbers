package org.hubbers.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.mcp.protocol.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP JSON-RPC request dispatcher.
 * Routes incoming JSON-RPC 2.0 requests to the appropriate handler method
 * and translates Hubbers execution results to MCP protocol responses.
 *
 * <p>Supported MCP methods:
 * <ul>
 *   <li>{@code initialize} — capability negotiation</li>
 *   <li>{@code notifications/initialized} — client acknowledgement (no response)</li>
 *   <li>{@code tools/list} — discover available tools</li>
 *   <li>{@code tools/call} — execute a tool</li>
 *   <li>{@code prompts/list} — discover available prompts</li>
 *   <li>{@code prompts/get} — get a specific prompt</li>
 * </ul>
 */
@Slf4j
public class McpRequestHandler {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String SERVER_NAME = "hubbers";
    private static final String SERVER_VERSION = "0.1.0";

    private static final String PREFIX_TOOL = "tool.";
    private static final String PREFIX_PIPELINE = "pipeline.";
    private static final String PREFIX_AGENT = "agent.";

    private final McpToolProvider toolProvider;
    private final McpPromptProvider promptProvider;
    private final McpResourceProvider resourceProvider;
    private final RuntimeFacade runtimeFacade;
    private final ObjectMapper mapper;
    private final McpSessionManager sessionManager;

    /**
     * Creates a new McpRequestHandler.
     *
     * @param toolProvider     provides MCP tool definitions from Hubbers artifacts
     * @param promptProvider   provides MCP prompt definitions from Hubbers agents
     * @param runtimeFacade    executes Hubbers artifacts
     * @param mapper           Jackson mapper for JSON serialization
     */
    public McpRequestHandler(McpToolProvider toolProvider,
                             McpPromptProvider promptProvider,
                             RuntimeFacade runtimeFacade,
                             ObjectMapper mapper) {
        this(toolProvider, promptProvider, null, runtimeFacade, mapper);
    }

    /**
     * Creates a new McpRequestHandler with resource support.
     *
     * @param toolProvider     provides MCP tool definitions from Hubbers artifacts
     * @param promptProvider   provides MCP prompt definitions from Hubbers agents
     * @param resourceProvider provides MCP resource definitions (may be null)
     * @param runtimeFacade    executes Hubbers artifacts
     * @param mapper           Jackson mapper for JSON serialization
     */
    public McpRequestHandler(McpToolProvider toolProvider,
                             McpPromptProvider promptProvider,
                             McpResourceProvider resourceProvider,
                             RuntimeFacade runtimeFacade,
                             ObjectMapper mapper) {
        this.toolProvider = toolProvider;
        this.promptProvider = promptProvider;
        this.resourceProvider = resourceProvider;
        this.runtimeFacade = runtimeFacade;
        this.mapper = mapper;
        this.sessionManager = new McpSessionManager();
    }

    /**
     * Handles an incoming MCP JSON-RPC request.
     *
     * @param request the parsed JSON-RPC request
     * @return the JSON-RPC response, or empty for notifications
     */
    public Optional<McpResponse> handle(McpRequest request) {
        String method = request.getMethod();
        log.debug("MCP request: method={}, id={}", method, request.getId());

        return switch (method) {
            case "initialize" -> Optional.of(handleInitialize(request));
            case "notifications/initialized" -> {
                log.info("MCP client initialized");
                yield Optional.empty(); // Notifications have no response
            }
            case "tools/list" -> Optional.of(handleToolsList(request));
            case "tools/call" -> Optional.of(handleToolsCall(request));
            case "prompts/list" -> Optional.of(handlePromptsList(request));
            case "prompts/get" -> Optional.of(handlePromptsGet(request));
            case "resources/list" -> Optional.of(handleResourcesList(request));
            case "resources/read" -> Optional.of(handleResourcesRead(request));
            case "ping" -> Optional.of(McpResponse.success(request.getId(), Map.of()));
            default -> {
                log.warn("Unknown MCP method: {}", method);
                yield Optional.of(McpResponse.methodNotFound(request.getId(), method));
            }
        };
    }

    private McpResponse handleInitialize(McpRequest request) {
        log.info("MCP initialize: negotiating capabilities");

        Map<String, Object> capabilities = Map.of(
                "tools", Map.of("listChanged", false),
                "prompts", Map.of("listChanged", false),
                "resources", Map.of("listChanged", false)
        );

        Map<String, Object> serverInfo = Map.of(
                "name", SERVER_NAME,
                "version", SERVER_VERSION
        );

        Map<String, Object> result = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", capabilities,
                "serverInfo", serverInfo
        );

        return McpResponse.success(request.getId(), result);
    }

    private McpResponse handleToolsList(McpRequest request) {
        List<McpToolInfo> tools = toolProvider.listTools();
        return McpResponse.success(request.getId(), Map.of("tools", tools));
    }

    private McpResponse handleToolsCall(McpRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("name")) {
            return McpResponse.invalidParams(request.getId(), "missing 'name' field");
        }

        String fullName = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();
        String sessionId = params.has("_meta") && params.get("_meta").has("sessionId")
                ? params.get("_meta").get("sessionId").asText() : null;

        log.info("MCP tools/call: {} (session={})", fullName, sessionId);

        try {
            RunResult result = routeAndExecute(fullName, arguments, sessionId);
            return McpResponse.success(request.getId(), toMcpToolCallResult(result));
        } catch (IllegalArgumentException e) {
            log.error("MCP tools/call failed for '{}': {}", fullName, e.getMessage());
            return McpResponse.success(request.getId(), McpToolCallResult.error(e.getMessage()));
        } catch (Exception e) {
            log.error("MCP tools/call error for '{}': {}", fullName, e.getMessage(), e);
            return McpResponse.internalError(request.getId(), e.getMessage());
        }
    }

    private McpResponse handlePromptsList(McpRequest request) {
        List<McpPromptInfo> prompts = promptProvider.listPrompts();
        return McpResponse.success(request.getId(), Map.of("prompts", prompts));
    }

    private McpResponse handlePromptsGet(McpRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("name")) {
            return McpResponse.invalidParams(request.getId(), "missing 'name' field");
        }

        String name = params.get("name").asText();
        String requestArg = null;
        if (params.has("arguments") && params.get("arguments").has("request")) {
            requestArg = params.get("arguments").get("request").asText();
        }

        Optional<McpPromptResult> result = promptProvider.getPrompt(name, requestArg);
        if (result.isEmpty()) {
            return McpResponse.invalidParams(request.getId(), "prompt not found: " + name);
        }

        return McpResponse.success(request.getId(), result.get());
    }

    private McpResponse handleResourcesList(McpRequest request) {
        if (resourceProvider == null) {
            return McpResponse.success(request.getId(), Map.of("resources", List.of()));
        }
        List<McpResourceInfo> resources = resourceProvider.listResources();
        return McpResponse.success(request.getId(), Map.of("resources", resources));
    }

    private McpResponse handleResourcesRead(McpRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("uri")) {
            return McpResponse.invalidParams(request.getId(), "missing 'uri' field");
        }
        String uri = params.get("uri").asText();
        if (resourceProvider == null) {
            return McpResponse.invalidParams(request.getId(), "resources not configured");
        }
        var content = resourceProvider.readResource(uri);
        if (content.isEmpty()) {
            return McpResponse.invalidParams(request.getId(), "resource not found: " + uri);
        }
        return McpResponse.success(request.getId(), Map.of(
                "contents", List.of(Map.of("uri", uri, "text", content.get()))
        ));
    }

    /**
     * Routes an MCP tool call to the appropriate Hubbers executor based on the name prefix.
     *
     * @param fullName  the prefixed tool name (e.g., "tool.web.search")
     * @param arguments the JSON arguments for the tool
     * @return the execution result
     * @throws IllegalArgumentException if the name prefix is unknown
     */
    /**
     * Routes an MCP tool call to the appropriate Hubbers executor based on the name prefix.
     * Agent calls use session-based conversation IDs for multi-turn dialogue.
     *
     * @param fullName  the prefixed tool name (e.g., "tool.web.search")
     * @param arguments the JSON arguments for the tool
     * @return the execution result
     * @throws IllegalArgumentException if the name prefix is unknown
     */
    private RunResult routeAndExecute(String fullName, JsonNode arguments) {
        return routeAndExecute(fullName, arguments, null);
    }

    private RunResult routeAndExecute(String fullName, JsonNode arguments, String sessionId) {
        if (fullName.startsWith(PREFIX_TOOL)) {
            String artifactName = fullName.substring(PREFIX_TOOL.length());
            return runtimeFacade.runTool(artifactName, arguments);
        }
        if (fullName.startsWith(PREFIX_PIPELINE)) {
            String artifactName = fullName.substring(PREFIX_PIPELINE.length());
            return runtimeFacade.runPipeline(artifactName, arguments);
        }
        if (fullName.startsWith(PREFIX_AGENT)) {
            String artifactName = fullName.substring(PREFIX_AGENT.length());
            // Use session conversation ID for agent continuity
            McpSessionManager.McpSession session = sessionManager.getOrCreate(sessionId);
            return runtimeFacade.runAgent(artifactName, arguments, session.getConversationId());
        }
        throw new IllegalArgumentException("Unknown artifact type prefix in tool name: " + fullName);
    }

    /**
     * Converts a Hubbers RunResult to an MCP McpToolCallResult.
     *
     * @param result the execution result
     * @return the MCP tool call result
     */
    private McpToolCallResult toMcpToolCallResult(RunResult result) {
        if (result.getStatus() == ExecutionStatus.FAILED) {
            String errorMsg = result.getError() != null ? result.getError() : "Execution failed";
            return McpToolCallResult.error(errorMsg);
        }

        String output;
        if (result.getOutput() != null) {
            output = result.getOutput().toString();
        } else {
            output = "{}";
        }
        return McpToolCallResult.success(output);
    }
}
