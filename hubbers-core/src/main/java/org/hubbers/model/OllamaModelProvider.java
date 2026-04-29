package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.config.OllamaConfig;
import org.hubbers.util.HttpRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Model provider implementation for Ollama local models.
 * 
 * <p>Supports both single-shot requests and multi-turn conversations with
 * function calling capabilities. Uses HttpRequestBuilder for clean HTTP
 * operations.</p>
 * 
 * @since 0.1.0
 */
public class OllamaModelProvider implements ModelProvider {
    private static final Logger log = LoggerFactory.getLogger(OllamaModelProvider.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2:3b";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final OllamaConfig config;
    private final Duration requestTimeout;

    public OllamaModelProvider(HttpClient httpClient, OllamaConfig config) {
        this(httpClient, config, org.hubbers.util.JacksonFactory.jsonMapper());
    }

    public OllamaModelProvider(HttpClient httpClient, OllamaConfig config, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.config = config;
        this.mapper = mapper;
        this.requestTimeout = Duration.ofSeconds(config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 120);
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

            // Suppress reasoning chain for thinking models (e.g. qwen3).
            // Must be top-level in the Ollama request, NOT inside "options".
            if (request.getThink() != null) {
                payload.put("think", request.getThink());
            }

            // Only force JSON format if no functions are defined
            // (function calling requires flexible output format)
            if (request.getFunctions() == null || request.getFunctions().isEmpty()) {
                payload.put("format", "json");
            }

            ArrayNode messages = payload.putArray("messages");
            
            // Support both single-request (systemPrompt/userPrompt) and multi-turn (messages) modes
            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                // Multi-turn conversation mode (used by AgentExecutor)
                for (Message msg : request.getMessages()) {
                    ObjectNode msgNode = messages.addObject();
                    msgNode.put("role", msg.getRole());
                    msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");
                    
                    // Add tool_call_id for tool role messages
                    if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
                        msgNode.put("tool_call_id", msg.getToolCallId());
                    }
                    
                    // Add tool_calls array for assistant messages with function calls
                    if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        ArrayNode toolCallsArray = msgNode.putArray("tool_calls");
                        for (FunctionCall fc : msg.getToolCalls()) {
                            ObjectNode tcNode = toolCallsArray.addObject();
                            tcNode.put("id", fc.getId());
                            tcNode.put("type", "function");
                            ObjectNode funcNode = tcNode.putObject("function");
                            funcNode.put("name", fc.getName());
                            funcNode.set("arguments", fc.getArguments());
                        }
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
                    
                    // Enrich description with examples if available
                    String description = func.getDescription();
                    if (func.getExamples() != null && !func.getExamples().isEmpty()) {
                        StringBuilder enriched = new StringBuilder(description);
                        enriched.append("\n\nExamples:");
                        for (int i = 0; i < Math.min(func.getExamples().size(), 2); i++) {
                            var example = func.getExamples().get(i);
                            enriched.append("\n").append(i + 1).append(". ");
                            if (example.getDescription() != null) {
                                enriched.append(example.getDescription()).append(": ");
                            }
                            try {
                                enriched.append(mapper.writeValueAsString(example.getInput()));
                            } catch (Exception e) {
                                enriched.append(example.getInput().toString());
                            }
                        }
                        description = enriched.toString();
                    }
                    function.put("description", description);
                    
                    // Add parameters schema (convert to proper JSON Schema format)
                    if (func.getParameters() != null) {
                        JsonNode normalizedSchema = normalizeSchema(func.getParameters());
                        function.set("parameters", normalizedSchema);
                    }
                }
                log.debug("Added {} function definitions for tool calling", request.getFunctions().size());
            }
            
            log.debug("Ollama request payload: {}", payload.toPrettyString());

            log.debug("Sending HTTP request to Ollama (timeout={}s)...", requestTimeout.getSeconds());
            
            JsonNode root = new HttpRequestBuilder(httpClient, mapper)
                    .post(baseUrl + "/api/chat")
                    .timeout(requestTimeout)
                    .body(payload)
                    .executeForJson();
            
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Ollama HTTP response received: elapsedMs={}", elapsed);
            
            JsonNode messageNode = root.path("message");
            String content = messageNode.path("content").asText();
            
            log.debug("Ollama response parsed: contentLength={}", content.length());
            log.trace("Ollama response content: {}", content);

            ModelResponse modelResponse = new ModelResponse();
            modelResponse.setContent(content);
            modelResponse.setModel(root.path("model").asText(model));
            modelResponse.setLatencyMs(System.currentTimeMillis() - start);

            // Extract token usage from Ollama response
            long promptToks = root.path("prompt_eval_count").asLong(0);
            long completionToks = root.path("eval_count").asLong(0);
            modelResponse.setPromptTokens(promptToks);
            modelResponse.setCompletionTokens(completionToks);
            modelResponse.setTotalTokens(promptToks + completionToks);
            log.debug("Ollama token usage: prompt={}, completion={}, total={}", promptToks, completionToks, promptToks + completionToks);

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
            log.error("Ollama request timed out after {}ms (timeout={}s)", elapsed, requestTimeout.getSeconds());
            throw new IllegalStateException("Ollama request timed out after " + requestTimeout.getSeconds() + "s", e);
        } catch (IOException e) {
            log.error("Ollama call failed with IOException: {}", e.getMessage(), e);
            throw new IllegalStateException(formatErrorMessage(e), e);
        }
    }

    private String formatErrorMessage(IOException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Ollama call failed";
        }

        if (message.startsWith("HTTP ")) {
            int separator = message.indexOf(':');
            if (separator > 5) {
                String prefix = message.substring(5, separator).trim();
                String details = message.substring(separator + 1).trim();
                return "Ollama error " + prefix + (details.isEmpty() ? "" : ": " + details);
            }
        }

        return "Ollama call failed: " + message;
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
