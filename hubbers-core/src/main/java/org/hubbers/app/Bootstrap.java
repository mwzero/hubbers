package org.hubbers.app;

import org.hubbers.config.OllamaConfig;
import org.hubbers.config.ConfigLoader;
import org.hubbers.config.AppConfig;
import org.hubbers.config.ExecutionsConfig;
import org.hubbers.config.RepoPathResolver;
import org.hubbers.execution.ExecutionStorageService;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.model.AnthropicModelProvider;
import org.hubbers.model.AzureOpenAiModelProvider;
import org.hubbers.model.LlamaCppModelProvider;
import org.hubbers.model.ModelProvider;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.model.OllamaModelProvider;
import org.hubbers.model.OpenAiModelProvider;
import org.hubbers.pipeline.InputMapper;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.security.ToolPermissionService;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolDriverContext;
import org.hubbers.tool.ToolDriverProvider;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.validation.SchemaValidator;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
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

        // Wire security: tool permission service
        var permissionService = new ToolPermissionService(config.getSecurity());
        toolExecutor.setPermissionService(permissionService);

        // Agentic capabilities (tool calling + memory)
        var conversationMemory = new org.hubbers.agent.memory.FileSystemConversationStore(
                Path.of(resolvedRepoPath, "_conversations"), jsonMapper);
        var modelRegistry = new ModelProviderRegistry(buildModelProviders(httpClient, config));
        
        // Create ExecutorRegistry to break circular dependency between AgentExecutor and PipelineExecutor
        var executorRegistry = new ExecutorRegistry();
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, toolExecutor);

        // Create unified AgentExecutor (handles both simple and agentic execution)
        var promptBuilder = new org.hubbers.agent.AgentPromptBuilder();
        var agentExecutor = new org.hubbers.agent.AgentExecutor(
                modelRegistry,
                promptBuilder,
                schemaValidator,
                jsonMapper,
                toolExecutor,
                repository,
                conversationMemory,
                executorRegistry
        );

        // Register AgentExecutor in registry
        executorRegistry.register(ExecutorRegistry.ExecutorType.AGENT, agentExecutor);

        // Create PipelineExecutor with ExecutorRegistry (instead of direct AgentExecutor reference)
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

        return new RuntimeFacade(repository, agentExecutor, toolExecutor, pipelineExecutor, skillExecutor, new ManifestValidator(), executionStorage, juiFormService);
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

    /**
     * Build the list of model providers based on configuration.
     * Only registers a provider if its config section exists and has the required credentials.
     */
    private static List<ModelProvider> buildModelProviders(HttpClient httpClient, AppConfig config) {
        List<ModelProvider> providers = new ArrayList<>();

        // Always register OpenAI and Ollama (Ollama works without config)
        providers.add(new OpenAiModelProvider(httpClient, config.getOpenai()));
        providers.add(new OllamaModelProvider(httpClient,
                config.getOllama() != null ? config.getOllama() : new OllamaConfig()));

        // Conditionally register Azure OpenAI
        if (config.getAzureOpenai() != null && config.getAzureOpenai().getApiKey() != null) {
            providers.add(new AzureOpenAiModelProvider(httpClient, config.getAzureOpenai()));
            log.info("Registered Azure OpenAI model provider");
        }

        // Conditionally register Anthropic
        if (config.getAnthropic() != null && config.getAnthropic().getApiKey() != null) {
            providers.add(new AnthropicModelProvider(httpClient, config.getAnthropic()));
            log.info("Registered Anthropic model provider");
        }

        // Register llama.cpp (works without API key — local server)
        if (config.getLlamaCpp() != null) {
            providers.add(new LlamaCppModelProvider(httpClient, config.getLlamaCpp()));
            log.info("Registered llama.cpp model provider at {}", config.getLlamaCpp().getBaseUrl());
        }

        log.info("Registered {} model provider(s)", providers.size());
        return providers;
    }
}
