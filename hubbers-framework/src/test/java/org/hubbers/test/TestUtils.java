package org.hubbers.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.agent.InputDefinition;
import org.hubbers.manifest.agent.Instructions;
import org.hubbers.manifest.agent.ModelConfig;
import org.hubbers.manifest.agent.OutputDefinition;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.SchemaDefinition;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.*;
import org.hubbers.util.JacksonFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Test utilities for creating test fixtures and mocks.
 * 
 * <p>Provides factory methods for creating valid test manifests, mock providers,
 * and common test data structures. Use these utilities to reduce duplication
 * in test code and ensure consistent test data.</p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * @Test
 * void testAgentExecution() {
 *     AgentManifest manifest = TestUtils.createTestAgentManifest("test.agent");
 *     ModelProvider provider = TestUtils.createMockProvider("test response");
 *     JsonNode input = TestUtils.createInput("query", "test query");
 *     
 *     // Test code...
 * }
 * }</pre>
 * 
 * @since 0.1.0
 */
public class TestUtils {
    
    private static final ObjectMapper MAPPER = JacksonFactory.jsonMapper();
    
    /**
     * Create a mock ModelProvider that returns fixed responses.
     * 
     * @param response the response content to return
     * @return a mock ModelProvider instance
     */
    public static ModelProvider createMockProvider(String response) {
        return createMockProvider(response, null);
    }
    
    /**
     * Create a mock ModelProvider with function calling support.
     * 
     * @param response the response content to return
     * @param functionCalls optional function calls to return
     * @return a mock ModelProvider instance
     */
    public static ModelProvider createMockProvider(String response, List<FunctionCall> functionCalls) {
        return new ModelProvider() {
            @Override
            public String providerName() {
                return "test";
            }
            
            @Override
            public ModelResponse generate(ModelRequest request) {
                ModelResponse modelResponse = new ModelResponse();
                modelResponse.setContent(response);
                modelResponse.setModel("test-model");
                modelResponse.setLatencyMs(10);
                modelResponse.setFunctionCalls(functionCalls);
                modelResponse.setFinishReason(functionCalls != null ? "function_call" : "stop");
                return modelResponse;
            }
        };
    }
    
    /**
     * Create a test AgentManifest with minimal valid configuration.
     * 
     * @param name the agent name
     * @return a valid AgentManifest for testing
     */
    public static AgentManifest createTestAgentManifest(String name) {
        return createTestAgentManifest(name, "Test agent description", "You are a test agent.");
    }
    
    /**
     * Create a test AgentManifest with custom configuration.
     * 
     * @param name the agent name
     * @param description the agent description
     * @param systemPrompt the system prompt
     * @return a valid AgentManifest for testing
     */
    public static AgentManifest createTestAgentManifest(String name, String description, String systemPrompt) {
        AgentManifest manifest = new AgentManifest();
        
        // Set metadata
        Metadata metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion("1.0.0");
        metadata.setDescription(description);
        manifest.setAgent(metadata);
        
        // Set model config
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setProvider("openai");
        modelConfig.setName("gpt-4");
        modelConfig.setTemperature(0.7);
        manifest.setModel(modelConfig);
        
        // Set instructions
        Instructions instructions = new Instructions(systemPrompt);
        manifest.setInstructions(instructions);
        
        // Set input/output schemas
        InputDefinition input = new InputDefinition();
        input.setSchema(createTestSchema("object"));
        manifest.setInput(input);
        
        OutputDefinition output = new OutputDefinition();
        output.setSchema(createTestSchema("object"));
        manifest.setOutput(output);
        
        return manifest;
    }
    
    /**
     * Create a test ToolManifest with minimal valid configuration.
     * 
     * @param name the tool name
     * @param type the tool type (e.g., "java", "http")
     * @return a valid ToolManifest for testing
     */
    public static ToolManifest createTestToolManifest(String name, String type) {
        ToolManifest manifest = new ToolManifest();
        
        // Set metadata
        Metadata metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion("1.0.0");
        metadata.setDescription("Test tool");
        manifest.setTool(metadata);
        
        manifest.setType(type);
        
        InputDefinition input = new InputDefinition();
        input.setSchema(createTestSchema("object"));
        manifest.setInput(input);
        
        OutputDefinition output = new OutputDefinition();
        output.setSchema(createTestSchema("object"));
        manifest.setOutput(output);
        
        return manifest;
    }
    
    /**
     * Create a test schema definition.
     * 
     * @param type the schema type (e.g., "object", "string")
     * @return a SchemaDefinition for testing
     */
    public static SchemaDefinition createTestSchema(String type) {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setType(type);
        return schema;
    }
    
    /**
     * Create a test input JsonNode with a single field.
     * 
     * @param fieldName the field name
     * @param fieldValue the field value
     * @return a JsonNode for testing
     */
    public static JsonNode createInput(String fieldName, String fieldValue) {
        ObjectNode input = MAPPER.createObjectNode();
        input.put(fieldName, fieldValue);
        return input;
    }
    
    /**
     * Create a test input JsonNode with multiple fields.
     * 
     * @param fields alternating field names and values
     * @return a JsonNode for testing
     */
    public static JsonNode createInput(String... fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("Fields must be in name-value pairs");
        }
        
        ObjectNode input = MAPPER.createObjectNode();
        for (int i = 0; i < fields.length; i += 2) {
            input.put(fields[i], fields[i + 1]);
        }
        return input;
    }
    
    /**
     * Create an empty input JsonNode.
     * 
     * @return an empty JsonNode for testing
     */
    public static JsonNode createEmptyInput() {
        return MAPPER.createObjectNode();
    }
    
    /**
     * Parse JSON string to JsonNode.
     * 
     * @param json the JSON string
     * @return the parsed JsonNode
     */
    public static JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
    
    /**
     * Create a test FunctionCall.
     * 
     * @param id the function call ID
     * @param name the function name
     * @param arguments the function arguments as JSON string
     * @return a FunctionCall for testing
     */
    public static FunctionCall createFunctionCall(String id, String name, String arguments) {
        return new FunctionCall(id, name, parseJson(arguments));
    }
    
    /**
     * Create a test FunctionDefinition.
     * 
     * @param name the function name
     * @param description the function description
     * @return a FunctionDefinition for testing
     */
    public static FunctionDefinition createFunctionDefinition(String name, String description) {
        JsonNode parameters = MAPPER.createObjectNode()
            .put("type", "object");
        return new FunctionDefinition(name, description, parameters);
    }
    
    /**
     * Create a list with a single item.
     * 
     * @param <T> the item type
     * @param item the item to add
     * @return a list containing the item
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) {
            list.add(item);
        }
        return list;
    }
    
    /**
     * Assert that a JsonNode contains a specific field.
     * 
     * @param node the JsonNode to check
     * @param fieldName the field name
     * @return true if the field exists
     */
    public static boolean hasField(JsonNode node, String fieldName) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull();
    }
    
    /**
     * Get a string value from a JsonNode.
     * 
     * @param node the JsonNode
     * @param fieldName the field name
     * @return the field value or null
     */
    public static String getString(JsonNode node, String fieldName) {
        return hasField(node, fieldName) ? node.get(fieldName).asText() : null;
    }
}
