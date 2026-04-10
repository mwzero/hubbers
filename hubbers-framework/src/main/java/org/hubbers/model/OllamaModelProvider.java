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
            
            // Only force JSON format if no functions are defined
            // (function calling requires flexible output format)
            if (request.getFunctions() == null || request.getFunctions().isEmpty()) {
                payload.put("format", "json");
            }

            ArrayNode messages = payload.putArray("messages");
            
            // Support both single-request (systemPrompt/userPrompt) and multi-turn (messages) modes
            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                // Multi-turn conversation mode (used by AgenticExecutor)
                for (Message msg : request.getMessages()) {
                    ObjectNode msgNode = messages.addObject();
                    msgNode.put("role", msg.getRole());
                    msgNode.put("content", msg.getContent());
                    
                    // Add tool_call_id for tool role messages
                    if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
                        msgNode.put("tool_call_id", msg.getToolCallId());
                    }
                }
            } else {
                // Single-request mode (used by AgentExecutor)
                messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
                messages.addObject().put("role", "user").put("content", request.getUserPrompt());
            }

            if (request.getTemperature() != null) {
                payload.putObject("options").put("temperature", request.getTemperature());
            }
            
            // Add function/tool definitions if available (for function calling)
            if (request.getFunctions() != null && !request.getFunctions().isEmpty()) {
                ArrayNode tools = payload.putArray("tools");
                for (FunctionDefinition func : request.getFunctions()) {
                    ObjectNode tool = tools.addObject();
                    tool.put("type", "function");
                    ObjectNode function = tool.putObject("function");
                    function.put("name", func.getName());
                    function.put("description", func.getDescription());
                    
                    // Add parameters schema (convert to proper JSON Schema format)
                    if (func.getParameters() != null) {
                        JsonNode normalizedSchema = normalizeSchema(func.getParameters());
                        function.set("parameters", normalizedSchema);
                    }
                }
                log.debug("Added {} function definitions for tool calling", request.getFunctions().size());
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
            JsonNode messageNode = root.path("message");
            String content = messageNode.path("content").asText();
            
            log.debug("Ollama response parsed: contentLength={}", content.length());
            log.trace("Ollama response content: {}", content);

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(root.path("model").asText(model));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);
            
            // Parse tool calls if present
            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                log.debug("Ollama returned {} tool calls", toolCallsNode.size());
                java.util.List<FunctionCall> functionCalls = new java.util.ArrayList<>();
                
                for (JsonNode toolCallNode : toolCallsNode) {
                    String callId = toolCallNode.path("id").asText();
                    JsonNode functionNode = toolCallNode.path("function");
                    String functionName = functionNode.path("name").asText();
                    
                    // Get arguments - could be string or object
                    JsonNode argumentsNode = functionNode.path("arguments");
                    JsonNode arguments;
                    
                    if (argumentsNode.isTextual()) {
                        // Arguments as JSON string - parse it
                        String argumentsStr = argumentsNode.asText();
                        log.debug("Function {} arguments (string): {}", functionName, argumentsStr);
                        try {
                            arguments = mapper.readTree(argumentsStr);
                        } catch (Exception e) {
                            log.error("Failed to parse arguments string for {}: {}", functionName, e.getMessage());
                            continue;
                        }
                    } else if (argumentsNode.isObject()) {
                        // Arguments already as object - use directly
                        arguments = argumentsNode;
                        log.debug("Function {} arguments (object): {}", functionName, arguments.toString());
                    } else {
                        // Empty or invalid arguments
                        log.warn("Function {} has invalid arguments type: {}", functionName, argumentsNode.getNodeType());
                        arguments = mapper.createObjectNode(); // Empty object as fallback
                    }
                    
                    FunctionCall funcCall = new FunctionCall(callId, functionName, arguments);
                    functionCalls.add(funcCall);
                    log.debug("Parsed function call: name={}, id={}, argsSize={}", 
                        functionName, callId, arguments.size());
                }
                
                modelResponse.setFunctionCalls(functionCalls);
                modelResponse.setFinishReason("function_call");
            } else {
                // No tool calls, normal completion
                modelResponse.setFinishReason("stop");
            }
            
            log.debug("Ollama call completed successfully: latencyMs={}, finishReason={}", 
                modelResponse.getLatencyMs(), modelResponse.getFinishReason());
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

    /**
     * Normalize schema format from Hubbers internal format to JSON Schema format.
     * Converts per-property "required": boolean to schema-level "required": string array.
     * 
     * Internal format: {"type": "object", "properties": {"field": {"type": "string", "required": true}}}
     * JSON Schema format: {"type": "object", "properties": {"field": {"type": "string"}}, "required": ["field"]}
     */
    private JsonNode normalizeSchema(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return schema;
        }
        
        ObjectNode normalized = mapper.createObjectNode();
        
        // Copy type
        if (schema.has("type")) {
            normalized.set("type", schema.get("type"));
        }
        
        // Process properties
        JsonNode propertiesNode = schema.get("properties");
        if (propertiesNode != null && propertiesNode.isObject()) {
            ObjectNode normalizedProperties = mapper.createObjectNode();
            ArrayNode requiredFields = mapper.createArrayNode();
            
            propertiesNode.fields().forEachRemaining(entry -> {
                String propName = entry.getKey();
                JsonNode propValue = entry.getValue();
                
                if (propValue.isObject()) {
                    ObjectNode normalizedProp = mapper.createObjectNode();
                    
                    // Check if required (boolean field)
                    boolean isRequired = propValue.has("required") && propValue.get("required").asBoolean(false);
                    if (isRequired) {
                        requiredFields.add(propName);
                    }
                    
                    // Copy other fields (type, description, etc.) except "required"
                    propValue.fields().forEachRemaining(propField -> {
                        String fieldName = propField.getKey();
                        if (!"required".equals(fieldName)) {
                            normalizedProp.set(fieldName, propField.getValue());
                        }
                    });
                    
                    normalizedProperties.set(propName, normalizedProp);
                } else {
                    normalizedProperties.set(propName, propValue);
                }
            });
            
            normalized.set("properties", normalizedProperties);
            
            // Add required array if any fields are required
            if (requiredFields.size() > 0) {
                normalized.set("required", requiredFields);
            }
        }
        
        return normalized;
    }
}
