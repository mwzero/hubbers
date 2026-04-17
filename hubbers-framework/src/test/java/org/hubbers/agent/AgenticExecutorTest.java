package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.agent.memory.ConversationMemory;
import org.hubbers.agent.memory.Fact;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.*;
import org.hubbers.test.TestUtils;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgenticExecutor - the ReAct loop implementation.
 * 
 * <p>Tests cover:
 * <ul>
 *   <li>Basic agent execution</li>
 *   <li>Tool calling (function calling)</li>
 *   <li>Multi-turn conversation</li>
 *   <li>ReAct loop iterations</li>
 *   <li>Error handling</li>
 * </ul>
 * </p>
 */
@DisplayName("AgenticExecutor Tests")
class AgenticExecutorTest {
    
    private AgenticExecutor agenticExecutor;
    private TestModelProvider mockModelProvider;
    private TestToolExecutor mockToolExecutor;
    private TestConversationMemory mockMemory;
    private TestArtifactRepository mockRepository;
    private SchemaValidator schemaValidator;
    private ExecutorRegistry executorRegistry;
    private AgentExecutor agentExecutor;
    private ObjectMapper mapper;
    
    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        schemaValidator = new SchemaValidator();
        
        // Create mock providers
        mockModelProvider = new TestModelProvider();
        ModelProviderRegistry modelRegistry = new ModelProviderRegistry(List.of(mockModelProvider));
        
        // Create mock executors and services
        mockToolExecutor = new TestToolExecutor();
        mockRepository = new TestArtifactRepository();
        mockMemory = new TestConversationMemory();
        executorRegistry = new ExecutorRegistry();
        
        // Create agent executor (for single-shot execution within ReAct loop)
        AgentPromptBuilder promptBuilder = new AgentPromptBuilder();
        agentExecutor = new AgentExecutor(modelRegistry, promptBuilder, schemaValidator, mapper);
        
        // Create main executor under test
        agenticExecutor = new AgenticExecutor(
            modelRegistry,
            mockToolExecutor,
            mockRepository,
            schemaValidator,
            mockMemory,
            executorRegistry,
            agentExecutor,
            mapper
        );
        
        // Register in registry
        executorRegistry.register(ExecutorRegistry.ExecutorType.AGENT, agenticExecutor);
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, mockToolExecutor);
    }
    
    @Test
    @DisplayName("Should execute agent successfully without tool calls")
    void testExecute_WithoutToolCalls_ReturnsSuccess() {
        // Given
        AgentManifest manifest = TestUtils.createTestAgentManifest("test.agent");
        JsonNode input = TestUtils.createInput("query", "What is 2+2?");
        
        mockModelProvider.setResponse("{\"answer\": \"4\"}");
        
        // When
        RunResult result = agenticExecutor.execute(manifest, input, null);
        
        // Then
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(TestUtils.hasField(result.getOutput(), "answer"));
    }
    
    @Test
    @DisplayName("Should call tool when model requests function call")
    void testExecute_WithToolCall_ExecutesTool() {
        // Given
        AgentManifest manifest = TestUtils.createTestAgentManifest("test.agent");
        JsonNode input = TestUtils.createInput("query", "Search for information");
        
        // Create tool manifest
        ToolManifest toolManifest = TestUtils.createTestToolManifest("test.tool", "java");
        mockRepository.addTool("test.tool", toolManifest);
        
        // First response: model requests tool call
        FunctionCall functionCall = TestUtils.createFunctionCall(
            "call-1", 
            "test.tool", 
            "{\"param\": \"value\"}"
        );
        mockModelProvider.setResponse("", List.of(functionCall));
        
        // Tool returns result
        mockToolExecutor.setToolResponse("test.tool", TestUtils.createInput("result", "tool output"));
        
        // Second response: model uses tool result
        mockModelProvider.setNextResponse("{\"answer\": \"Based on the tool result...\"}");
        
        // When
        RunResult result = agenticExecutor.execute(manifest, input, null);
        
        // Then
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue(mockToolExecutor.wasToolCalled("test.tool"));
    }
    
    @Test
    @DisplayName("Should maintain conversation history across calls")
    void testExecute_WithConversationId_MaintainsHistory() {
        // Given
        AgentManifest manifest = TestUtils.createTestAgentManifest("test.agent");
        String conversationId = "conv-123";
        
        // First message
        JsonNode input1 = TestUtils.createInput("query", "Hello");
        mockModelProvider.setResponse("{\"response\": \"Hi there!\"}");
        RunResult result1 = agenticExecutor.execute(manifest, input1, conversationId);
        
        // Second message (should have history)
        JsonNode input2 = TestUtils.createInput("query", "What did I say before?");
        mockModelProvider.setResponse("{\"response\": \"You said hello\"}");
        RunResult result2 = agenticExecutor.execute(manifest, input2, conversationId);
        
        // Then
        assertEquals(ExecutionStatus.SUCCESS, result1.getStatus());
        assertEquals(ExecutionStatus.SUCCESS, result2.getStatus());
        
        // Verify conversation history was maintained
        List<Message> history = mockMemory.loadHistory(conversationId);
        assertTrue(history.size() >= 2, "Should have at least 2 messages in history");
    }
    
    @Test
    @DisplayName("Should fail gracefully with invalid input")
    void testExecute_WithInvalidInput_ReturnsFailure() {
        // Given
        AgentManifest manifest = TestUtils.createTestAgentManifest("test.agent");
        JsonNode invalidInput = mapper.createObjectNode(); // Empty, assuming required fields
        
        // Modify manifest to require a field
        PropertyDefinition queryProp = new PropertyDefinition();
        queryProp.setType("string");
        queryProp.setRequired(true);
        manifest.getInput().getSchema().getProperties().put("query", queryProp);
        
        // When
        RunResult result = agenticExecutor.execute(manifest, invalidInput, null);
        
        // Then
        assertNotNull(result);
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertNotNull(result.getError());
    }
    
    // ===== Test Helper Classes =====
    
    /**
     * Mock ModelProvider for testing.
     */
    static class TestModelProvider implements ModelProvider {
        private String response;
        private List<FunctionCall> functionCalls;
        private List<String> nextResponses = new ArrayList<>();
        private int callCount = 0;
        
        void setResponse(String response) {
            this.response = response;
            this.functionCalls = null;
        }
        
        void setResponse(String response, List<FunctionCall> functionCalls) {
            this.response = response;
            this.functionCalls = functionCalls;
        }
        
        void setNextResponse(String response) {
            nextResponses.add(response);
        }
        
        @Override
        public String providerName() {
            return "test";
        }
        
        @Override
        public ModelResponse generate(ModelRequest request) {
            ModelResponse modelResponse = new ModelResponse();
            
            // Use next response if available, otherwise use default
            String content = response;
            List<FunctionCall> calls = functionCalls;
            
            if (!nextResponses.isEmpty()) {
                content = nextResponses.remove(0);
                calls = null; // Next responses are final responses
            }
            
            modelResponse.setContent(content);
            modelResponse.setModel("test-model");
            modelResponse.setLatencyMs(10);
            modelResponse.setFunctionCalls(calls);
            modelResponse.setFinishReason(calls != null ? "function_call" : "stop");
            
            callCount++;
            return modelResponse;
        }
        
        int getCallCount() {
            return callCount;
        }
    }
    
    /**
     * Mock ToolExecutor for testing.
     */
    static class TestToolExecutor extends ToolExecutor {
        private final java.util.Map<String, JsonNode> toolResponses = new java.util.HashMap<>();
        private final java.util.Set<String> calledTools = new java.util.HashSet<>();
        
        TestToolExecutor() {
            super(List.of(), new SchemaValidator());
        }
        
        void setToolResponse(String toolName, JsonNode response) {
            toolResponses.put(toolName, response);
        }
        
        @Override
        public RunResult execute(ToolManifest manifest, JsonNode input) {
            String toolName = manifest.getTool().getName();
            calledTools.add(toolName);
            
            JsonNode response = toolResponses.getOrDefault(toolName, 
                JacksonFactory.jsonMapper().createObjectNode().put("result", "default"));
            return RunResult.success(response);
        }
        
        boolean wasToolCalled(String toolName) {
            return calledTools.contains(toolName);
        }
    }
    
    /**
     * Mock ConversationMemory for testing.
     */
    static class TestConversationMemory implements ConversationMemory {
        private final java.util.Map<String, List<Message>> conversations = new java.util.HashMap<>();
        private final java.util.Map<String, java.util.Map<String, JsonNode>> facts = new java.util.HashMap<>();
        
        @Override
        public void saveMessage(String conversationId, Message message) {
            conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        }
        
        @Override
        public List<Message> loadHistory(String conversationId) {
            return conversations.getOrDefault(conversationId, new ArrayList<>());
        }
        
        @Override
        public void saveFact(String conversationId, String key, JsonNode value) {
            facts.computeIfAbsent(conversationId, k -> new java.util.HashMap<>()).put(key, value);
        }
        
        @Override
        public JsonNode getFact(String conversationId, String key) {
            return facts.getOrDefault(conversationId, new java.util.HashMap<>()).get(key);
        }
        
        @Override
        public List<Fact> searchFacts(String conversationId, String query) {
            return new ArrayList<>();
        }
        
        @Override
        public void clearConversation(String conversationId) {
            conversations.remove(conversationId);
            facts.remove(conversationId);
        }
    }
    
    /**
     * Mock ArtifactRepository for testing.
     */
    static class TestArtifactRepository extends ArtifactRepository {
        private final java.util.Map<String, ToolManifest> tools = new java.util.HashMap<>();
        
        TestArtifactRepository() {
            super(java.nio.file.Path.of("test"));
        }
        
        void addTool(String name, ToolManifest manifest) {
            tools.put(name, manifest);
        }
        
        @Override
        public ToolManifest loadTool(String name) {
            return tools.get(name);
        }
        
        @Override
        public List<String> listTools() {
            return new ArrayList<>(tools.keySet());
        }
    }
}
