package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.hubbers.agent.memory.ConversationMemory;
import org.hubbers.agent.memory.InMemoryConversationStore;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.config.OllamaConfig;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.model.OllamaModelProvider;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolDriverContext;
import org.hubbers.tool.ToolDriverProvider;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

@Slf4j
class AgentExecutorTest {

    ObjectMapper mapper;
    ObjectMapper yamlMapper;

    SchemaValidator schemaValidator = new SchemaValidator();
    ModelProviderRegistry modelRegistry;
    HttpClient  httpClient;
    ArtifactRepository repository;
    AgentExecutor agentExecutor;
    ExecutorRegistry executorRegistry;
    ConversationMemory conversationMemory = new InMemoryConversationStore();
    ToolExecutor toolExecutor;
    AgentPromptBuilder agentPromptBuilder = new AgentPromptBuilder();

    @BeforeEach
    void setUp() throws URISyntaxException {
        
        mapper = JacksonFactory.jsonMapper();
        yamlMapper = JacksonFactory.yamlMapper();

        var repo = Path.of(getClass().getClassLoader()
                .getResource("repo").toURI());

        OllamaConfig ollamaConfig = new OllamaConfig();
        ollamaConfig.setBaseUrl("http://localhost:11434");
        ollamaConfig.setTimeoutSeconds(300);

        httpClient = HttpClient.newHttpClient();
        modelRegistry = new ModelProviderRegistry(List.of(
                new OllamaModelProvider(httpClient, ollamaConfig)
        ));

        repository = new ArtifactRepository(repo);
        List<ToolDriver> drivers = loadToolDrivers(new ToolDriverContext(mapper, HttpClient.newHttpClient(), null));
        toolExecutor = new ToolExecutor(drivers, schemaValidator);
        conversationMemory = new InMemoryConversationStore();
        executorRegistry = new ExecutorRegistry();

        agentExecutor = new AgentExecutor(
            modelRegistry,
            agentPromptBuilder,
            schemaValidator,
            mapper,
            toolExecutor,
            repository,
            conversationMemory,
            executorRegistry
        );
        executorRegistry.register(ExecutorRegistry.ExecutorType.AGENT, agentExecutor);

    }
    
    @Test
    void executeSimpleAgent() throws Exception {
        
        AgentManifest manifest = repository.loadAgent("simple.agent");

        JsonNode input = mapper.readTree("{\"text\":\"list five cities in Europe\"}");
        RunResult result = agentExecutor.execute(manifest, input, null);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        System.out.println("Model output: " + result.getOutput().toPrettyString());
        assertTrue(result.getOutput().path("cities").isArray());
        assertTrue(result.getOutput().path("cities").size() > 0);
    }


    @Test
    void testExecute_WithToolCall_ExecutesTool() throws Exception {
        
        AgentManifest manifest = repository.loadAgent("simple.with.tools.agent");

        JsonNode input = mapper.readTree("{\"text\":\"What is the weather in Rome?\"}");
        RunResult result = agentExecutor.execute(manifest, input, null);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        System.out.println("Model output: " + result.getOutput().toPrettyString());
        assertTrue(result.getOutput().path("results").isArray());
        assertEquals(1, result.getOutput().path("results").size());
        JsonNode firstResult = result.getOutput().path("results").get(0);
        assertEquals("Rome", firstResult.path("city").asText());
        assertTrue(firstResult.path("temperature_celsius").isNumber());
        assertFalse(firstResult.path("condition").asText().isBlank());


    }

    @Test
    void testExecute_WithToolCall_ExecutesTool2() throws Exception {
        
        AgentManifest manifest = repository.loadAgent("simple.with.tools.agent");

        JsonNode input = mapper.readTree("{\"text\":\"che temperature ci sono a Roma, Bologna e Napoli?\"}");
        RunResult result = agentExecutor.execute(manifest, input, null);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        System.out.println("Model output: " + result.getOutput().toPrettyString());
        assertTrue(result.getOutput().path("results").isArray());
        assertEquals(3, result.getOutput().path("results").size());
    }

    @Test
    void testExecute_Agentic_WithToolCall() throws Exception {
        
        AgentManifest manifest = repository.loadAgent("tao.agent");

        JsonNode input = mapper.readTree("{\"text\":\"che temperature ci sono a Roma, Bologna e Napoli?\"}");
        RunResult result = agentExecutor.execute(manifest, input, null);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        System.out.println("Model output: " + result.getOutput().toPrettyString());
        assertTrue(result.getOutput().path("results").isArray());
        assertEquals(3, result.getOutput().path("results").size());
    }

    private static List<ToolDriver> loadToolDrivers(ToolDriverContext context) {
        List<ToolDriverProvider> providers = ServiceLoader.load(ToolDriverProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        List<ToolDriver> drivers = providers.stream()
                .flatMap(provider -> provider.createDrivers(context).stream())
                .toList();

        log.info("Loaded {} tool drivers from {} provider(s)", drivers.size(), providers.size());
        return drivers;
    }

}

