package org.hubbers.app;

import org.hubbers.config.OllamaConfig;
import org.hubbers.config.ConfigLoader;
import org.hubbers.config.AppConfig;
import org.hubbers.config.ExecutionsConfig;
import org.hubbers.config.RepoPathResolver;
import org.hubbers.execution.ExecutionStorageService;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.model.OllamaModelProvider;
import org.hubbers.model.OpenAiModelProvider;
import org.hubbers.pipeline.InputMapper;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolDriverContext;
import org.hubbers.tool.ToolDriverProvider;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.validation.SchemaValidator;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Bootstrap {

    public static RuntimeFacade createRuntimeFacade() {
        return createRuntimeFacade(null);
    }

    public static RuntimeFacade createRuntimeFacade(String repoPath) {
        String resolvedRepoPath = RepoPathResolver.resolve(repoPath);

        log.info("Initializing Hubbers Runtime with repo path: {}", resolvedRepoPath);
        // Load configuration application.yaml
        AppConfig config = new ConfigLoader(resolvedRepoPath).load();
        
        // Initialize execution storage
        ExecutionsConfig executionsConfig = config.getExecutions();
        String executionsPath = executionsConfig != null ? executionsConfig.getPath() : "./executions";
        ExecutionStorageService executionStorage = new ExecutionStorageService(executionsPath, resolvedRepoPath);


        var jsonMapper = JacksonFactory.jsonMapper();
        var httpClient = HttpClient.newHttpClient();

        var repository = new ArtifactRepository(Path.of(config.getRepoRoot()));
        

        var schemaValidator = new SchemaValidator();
        var toolExecutor = new ToolExecutor(loadToolDrivers(new ToolDriverContext(jsonMapper, httpClient, config)), schemaValidator);

        // Agentic capabilities (tool calling + memory)
        var conversationMemory = new org.hubbers.agent.memory.InMemoryConversationStore();
        var modelRegistry = new ModelProviderRegistry(List.of(
                new OpenAiModelProvider(httpClient, config.getOpenai()),
                new OllamaModelProvider(httpClient, config.getOllama() != null ? config.getOllama() : new OllamaConfig())
        ));
        
        // Create AgentExecutor for single-shot agent execution
        var promptBuilder = new org.hubbers.agent.AgentPromptBuilder();
        var agentExecutor = new org.hubbers.agent.AgentExecutor(
                modelRegistry,
                promptBuilder,
                schemaValidator,
                jsonMapper
        );
        
        // Create ExecutorRegistry to break circular dependency between AgenticExecutor and PipelineExecutor
        var executorRegistry = new ExecutorRegistry();
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, toolExecutor);
        
        // Create AgenticExecutor with ExecutorRegistry (instead of direct PipelineExecutor reference)
        var agenticExecutor = new org.hubbers.agent.AgenticExecutor(
                modelRegistry, 
                toolExecutor,
                repository, 
                schemaValidator, 
                conversationMemory,
                executorRegistry,  // Uses registry to find PipelineExecutor
                agentExecutor,
                jsonMapper
        );
        
        // Register AgenticExecutor in registry
        executorRegistry.register(ExecutorRegistry.ExecutorType.AGENT, agenticExecutor);

        // Create PipelineExecutor with ExecutorRegistry (instead of direct AgenticExecutor reference)
        var pipelineExecutor = new PipelineExecutor(repository, executorRegistry, toolExecutor, new InputMapper(jsonMapper));
        
        // Register PipelineExecutor in registry (completes the setup)
        executorRegistry.register(ExecutorRegistry.ExecutorType.PIPELINE, pipelineExecutor);

        
        // Initialize skill executor
        var skillExecutor = new org.hubbers.skill.SkillExecutor(
                modelRegistry,
                toolExecutor,
                repository,
                jsonMapper
        );
        
        // Register SkillExecutor in registry
        executorRegistry.register(ExecutorRegistry.ExecutorType.SKILL, skillExecutor);

        // Initialize form support
        var formSessionStore = new org.hubbers.forms.FormSessionStore();
        var juiFormService = new org.hubbers.forms.JuiFormService(formSessionStore);

        return new RuntimeFacade(repository, agenticExecutor, toolExecutor, pipelineExecutor, skillExecutor, new ManifestValidator(), executionStorage, juiFormService);
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
