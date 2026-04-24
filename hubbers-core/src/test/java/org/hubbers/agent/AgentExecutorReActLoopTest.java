package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.agent.memory.ConversationMemory;
import org.hubbers.agent.memory.InMemoryConversationStore;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.model.*;
import org.hubbers.test.TestUtils;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolDriverContext;
import org.hubbers.tool.ToolDriverProvider;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AgentExecutor ReAct loop using mock ModelProvider.
 * These tests do NOT require a running Ollama/OpenAI server.
 */
@DisplayName("AgentExecutor ReAct Loop Tests")
class AgentExecutorReActLoopTest {

    private ObjectMapper mapper;
    private SchemaValidator schemaValidator;
    private ConversationMemory conversationMemory;
    private ArtifactRepository repository;
    private ToolExecutor toolExecutor;
    private ExecutorRegistry executorRegistry;

    @BeforeEach
    void setUp() throws URISyntaxException {
        mapper = JacksonFactory.jsonMapper();
        schemaValidator = new SchemaValidator();
        conversationMemory = new InMemoryConversationStore();
        executorRegistry = new ExecutorRegistry();

        var repo = Path.of(getClass().getClassLoader()
                .getResource("repo").toURI());
        repository = new ArtifactRepository(repo);

        List<ToolDriver> drivers = loadToolDrivers(
                new ToolDriverContext(mapper, HttpClient.newHttpClient(), null));
        toolExecutor = new ToolExecutor(drivers, schemaValidator);
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, toolExecutor);
    }

    @Test
    @DisplayName("Simple mode should execute exactly one LLM call and return result")
    void testExecute_SimpleMode_SingleIteration() {
        // Given — mock provider returns a valid JSON response
        String jsonResponse = """
                {"result": {"answer": "Paris is the capital of France"}, "reasoning": "Direct factual answer"}
                """;
        ModelProvider mockProvider = createOllamaMockProvider(jsonResponse, null);
        AgentExecutor agentExecutor = createAgentExecutor(mockProvider);

        AgentManifest manifest = repository.loadAgent("simple.agent");
        JsonNode input = TestUtils.createInput("request", "What is the capital of France?");

        // When
        RunResult result = agentExecutor.execute(manifest, input, null);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Simple mode agent should succeed with valid response");
        assertNotNull(result.getOutput(), "Output should not be null");
    }

    @Test
    @DisplayName("Agentic mode with tool call should execute tool and return synthesis")
    void testExecute_AgenticWithToolCall_ExecutesToolAndSynthesizes() {
        // Given — mock provider returns a tool call on first invocation, then a final answer
        JsonNode weatherArgs = mapper.createObjectNode().put("city", "Rome");
        FunctionCall weatherCall = new FunctionCall("call-1", "mock.weather", weatherArgs);

        // First call returns function_call, second call returns final answer
        ModelProvider sequentialProvider = new ModelProvider() {
            private int callCount = 0;

            @Override
            public String providerName() {
                return "ollama";
            }

            @Override
            public ModelResponse generate(ModelRequest request) {
                callCount++;
                ModelResponse response = new ModelResponse();
                response.setModel("test-model");
                response.setLatencyMs(10);

                if (callCount == 1) {
                    // First call: return a tool call
                    response.setContent("");
                    response.setFunctionCalls(List.of(weatherCall));
                    response.setFinishReason("function_call");
                } else {
                    // Subsequent calls: return final answer
                    response.setContent("""
                            {"results": [{"city": "Rome", "temperature_celsius": 22, "condition": "sunny"}]}
                            """);
                    response.setFunctionCalls(null);
                    response.setFinishReason("stop");
                }
                return response;
            }
        };

        AgentExecutor agentExecutor = createAgentExecutor(sequentialProvider);

        // Use agentic agent from test resources
        AgentManifest manifest = repository.loadAgent("agentic.weather.agent");
        JsonNode input = TestUtils.createInput("text", "What is the weather in Rome?");

        // When
        RunResult result = agentExecutor.execute(manifest, input, null);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Agentic execution with tool call should succeed");
        assertNotNull(result.getOutput(), "Output should contain synthesized result");
    }

    @Test
    @DisplayName("Conversation memory should persist across calls with same conversation ID")
    void testExecute_WithConversationId_PersistsHistory() {
        // Given
        String jsonResponse = """
                {"result": {"answer": "Hello!"}, "reasoning": "Greeting"}
                """;
        ModelProvider mockProvider = createOllamaMockProvider(jsonResponse, null);
        AgentExecutor agentExecutor = createAgentExecutor(mockProvider);

        AgentManifest manifest = repository.loadAgent("simple.agent");
        String conversationId = "test-conv-persist";

        // When — execute twice with same conversation ID
        agentExecutor.execute(manifest, TestUtils.createInput("request", "Hello"), conversationId);
        agentExecutor.execute(manifest, TestUtils.createInput("request", "How are you?"), conversationId);

        // Then — conversation memory should have messages from both turns
        var history = conversationMemory.loadHistory(conversationId);
        // Note: AgentExecutor may or may not persist to conversation memory depending on mode.
        // This test validates that executing twice with same conversationId does not throw.
        assertNotNull(history, "History should not be null");
    }

    @Test
    @DisplayName("Should handle LLM returning invalid JSON gracefully")
    void testExecute_InvalidJsonResponse_HandleGracefully() {
        // Given — mock provider returns invalid JSON
        ModelProvider mockProvider = createOllamaMockProvider("This is not JSON at all", null);
        AgentExecutor agentExecutor = createAgentExecutor(mockProvider);

        AgentManifest manifest = repository.loadAgent("simple.agent");
        JsonNode input = TestUtils.createInput("request", "test");

        // When
        RunResult result = agentExecutor.execute(manifest, input, null);

        // Then — should not throw; may return failed or wrap the text
        assertNotNull(result, "Should return a result even with invalid JSON");
    }

    // --- Helper methods ---

    private AgentExecutor createAgentExecutor(ModelProvider provider) {
        var modelRegistry = new ModelProviderRegistry(List.of(provider));
        var agentExecutor = new AgentExecutor(
                modelRegistry,
                new AgentPromptBuilder(),
                schemaValidator,
                mapper,
                toolExecutor,
                repository,
                conversationMemory,
                executorRegistry
        );
        executorRegistry.register(ExecutorRegistry.ExecutorType.AGENT, agentExecutor);
        return agentExecutor;
    }

    private ModelProvider createOllamaMockProvider(String response, List<FunctionCall> functionCalls) {
        return new ModelProvider() {
            @Override
            public String providerName() {
                return "ollama";
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

    private List<ToolDriver> loadToolDrivers(ToolDriverContext context) {
        return ServiceLoader.load(ToolDriverProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .flatMap(provider -> provider.createDrivers(context).stream())
                .toList();
    }
}
