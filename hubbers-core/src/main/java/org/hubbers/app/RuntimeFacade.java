package org.hubbers.app;

import com.fasterxml.jackson.databind.JsonNode;

import org.hubbers.agent.AgentExecutor;
import org.hubbers.agent.ArtifactCatalogInjector;
import org.hubbers.execution.*;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.skill.SkillExecutor;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central facade for executing agents, tools, pipelines, and skills.
 * 
 * <p>RuntimeFacade provides a unified API for all artifact execution types with
 * automatic validation, tracing, and error handling. It's the main entry point
 * for both CLI and web interfaces.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Unified execution API for all artifact types</li>
 *   <li>Automatic input/output validation against schemas</li>
 *   <li>Execution tracing and logging</li>
 *   <li>Natural language task routing</li>
 *   <li>Conversation management</li>
 * </ul>
 * </p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * RuntimeFacade facade = Bootstrap.createRuntimeFacade();
 * JsonNode input = mapper.readTree("{\"query\":\"search term\"}");
 * RunResult result = facade.runAgent("demo.search", input);
 * if (result.getStatus() == ExecutionStatus.SUCCESS) {
 *     System.out.println("Output: " + result.getOutput());
 * }
 * }</pre>
 * 
 * @see Bootstrap#createRuntimeFacade()
 * @since 0.1.0
 */
public class RuntimeFacade {
    
    private static final Logger logger = LoggerFactory.getLogger(RuntimeFacade.class);
    
    private final ArtifactRepository artifactRepository;
    private final AgentExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final PipelineExecutor pipelineExecutor;
    private final SkillExecutor skillExecutor;
    private final ManifestValidator manifestValidator;
    private final ExecutionStorageService executionStorage;
    private final org.hubbers.forms.JuiFormService formService;
    
    private final ArtifactCatalogInjector artifactCatalogInjector = new ArtifactCatalogInjector();

    public RuntimeFacade(ArtifactRepository artifactRepository,
                         AgentExecutor agentExecutor,
                         ToolExecutor toolExecutor,
                         PipelineExecutor pipelineExecutor,
                         SkillExecutor skillExecutor,
                         ManifestValidator manifestValidator,
                         ExecutionStorageService executionStorage,
                         org.hubbers.forms.JuiFormService formService) {
        this.artifactRepository = artifactRepository;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.pipelineExecutor = pipelineExecutor;
        this.skillExecutor = skillExecutor;
        this.manifestValidator = manifestValidator;
        this.executionStorage = executionStorage;
        this.formService = formService;
    }

    public RuntimeFacade(Path of) {
        this(Bootstrap.createRuntimeFacade(of.toString()));
    }

    private RuntimeFacade(RuntimeFacade delegate) {
        this(
            delegate.artifactRepository,
            delegate.agentExecutor,
            delegate.toolExecutor,
            delegate.pipelineExecutor,
            delegate.skillExecutor,
            delegate.manifestValidator,
            delegate.executionStorage,
            delegate.formService
        );
    }

    /**
     * Execute an agent with optional conversation support and intelligent mode detection.
     * 
     * If input contains {"request": "natural language string"}, automatically loads
     * all artifacts, applies RAG-based filtering, injects them into the agent, and
     * executes via the agentic (ReAct) loop. Otherwise, executes the agent directly.
     * 
     * @param name Agent name
     * @param input Input JSON (either direct schema or {"request": "..."})
     * @return RunResult with execution status and output
     */
    public RunResult runAgent(String name, JsonNode input) {
        return runAgent(name, input, null);
    }
    
    /**
     * Execute an agent with conversation support.
     * 
     * @param name Agent name
     * @param input Input JSON
     * @param conversationId Optional conversation ID for multi-turn dialogue
     * @return RunResult with execution status and output
     */
    public RunResult runAgent(String name, JsonNode input, String conversationId) {
        if (isNaturalLanguageRequest(input)) {
            logger.info("Detected natural language request for agent '{}', routing through agentic executor", name);
            return executeNaturalLanguageTask(name, input, conversationId);
        }
        return executeWithTracking("agent", name, input, () -> {
            var manifest = artifactRepository.loadAgent(name);
            var validation = manifestValidator.validateAgent(manifest);
            if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
            return agentExecutor.execute(manifest, input, conversationId);
        });
    }
    
    /**
     * Check if input JSON represents a natural language task request.
     * Detects pattern: {"request": "string", "context": {...optional}}
     */
    private boolean isNaturalLanguageRequest(JsonNode input) {
        return input != null && 
               input.has("request") && 
               input.get("request").isTextual() &&
               !input.get("request").asText().trim().isEmpty();
    }

    /**
     * Execute a natural language task request with RAG-based artifact discovery and injection.
     */
    private RunResult executeNaturalLanguageTask(String agentName, JsonNode input, String conversationId) {
        String request = input.get("request").asText();
        JsonNode context = input.has("context") ? input.get("context") : null;
        String convId = (conversationId != null && !conversationId.isEmpty())
            ? conversationId : UUID.randomUUID().toString();

        return executeWithTracking("agent", agentName, input, () -> {
            AgentManifest agent = artifactRepository.loadAgent(agentName);
            if (agent == null) {
                return RunResult.failed("Agent not found: " + agentName);
            }

            // Load all available artifacts
            List<ToolManifest> allTools = loadAll(artifactRepository.listTools(),
                name -> artifactRepository.loadTool(name), "tool");
            List<AgentManifest> allAgents = loadAll(artifactRepository.listAgents(),
                name -> artifactRepository.loadAgent(name), "agent");
            List<PipelineManifest> allPipelines = loadAll(artifactRepository.listPipelines(),
                name -> artifactRepository.loadPipeline(name), "pipeline");

            logger.info("Loaded {} tools, {} agents, {} pipelines for NL task execution",
                allTools.size(), allAgents.size(), allPipelines.size());

            // Check if agent has pre-defined tools
            @SuppressWarnings("unchecked")
            List<String> preDefinedTools = agent.getConfig() != null
                ? (List<String>) agent.getConfig().get("tools") : null;
            boolean hasPreDefinedTools = preDefinedTools != null && !preDefinedTools.isEmpty();

            if (!hasPreDefinedTools) {
                logger.info("Applying RAG-based filtering for agent '{}'", agentName);
                var filtered = artifactCatalogInjector.filterByRelevance(
                    request, allTools, allAgents, allPipelines,
                    artifactRepository.getAllSkillMetadata(), 5);
                artifactCatalogInjector.injectAllArtifacts(
                    agent, filtered.tools(), filtered.agents(),
                    filtered.pipelines(), filtered.skills());
            } else {
                logger.info("Agent '{}' has pre-defined tools, skipping RAG filtering", agentName);
                artifactCatalogInjector.injectAllArtifacts(
                    agent, allTools, allAgents, allPipelines,
                    artifactRepository.getAllSkillMetadata());
            }

            // Build input with request + optional context
            com.fasterxml.jackson.databind.node.ObjectNode agentInput =
                JacksonFactory.jsonMapper().createObjectNode();
            agentInput.put("request", request);
            if (context != null) {
                agentInput.set("context", context);
            }

            return agentExecutor.execute(agent, agentInput, convId);
        });
    }

    @FunctionalInterface
    private interface ArtifactLoader<T> {
        T load(String name) throws Exception;
    }

    private <T> List<T> loadAll(List<String> names, ArtifactLoader<T> loader, String type) {
        List<T> result = new ArrayList<>();
        for (String name : names) {
            try {
                T artifact = loader.load(name);
                if (artifact != null) result.add(artifact);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed to load {} {}: {}", type, name, e.getMessage());
            } catch (Exception e) {
                logger.warn("Failed to load {} {}: {}", type, name, e.getMessage());
            }
        }
        return result;
    }

    public RunResult runTool(String name, JsonNode input) {
        return executeWithTracking("tool", name, input, () -> {
            var manifest = artifactRepository.loadTool(name);
            var validation = manifestValidator.validateTool(manifest);
            if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
            return toolExecutor.execute(manifest, input);
        });
    }

    public RunResult runPipeline(String name, JsonNode input) {
        return executeWithTracking("pipeline", name, input, () -> {
            var manifest = artifactRepository.loadPipeline(name);
            var validation = manifestValidator.validatePipeline(manifest);
            if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
            return pipelineExecutor.execute(manifest, input);
        });
    }

    public RunResult runSkill(String name, JsonNode input) {
        return executeWithTracking("skill", name, input, () -> {
            SkillManifest manifest = artifactRepository.loadSkill(name);
            // Skill validation is handled internally by SkillExecutor
            return skillExecutor.execute(manifest, input);
        });
    }
    
    /**
     * Wraps execution with tracking and logging.
     */
    private RunResult executeWithTracking(String type, String name, JsonNode input, ExecutionCallable callable) {
        // Generate execution ID
        String executionId = ExecutionIdGenerator.generate();
        
        // Create execution context
        ExecutionContext context = new ExecutionContext(executionId, type, name);
        context.setInput(input);
        
        try {
            // Create execution directory
            executionStorage.createExecutionDirectory(executionId);
            
            // Create logger
            ExecutionLogger execLogger = new ExecutionLogger(executionStorage, executionId);
            context.setLogger(execLogger);
            
            // Set context in thread-local holder for nested executors
            ExecutionContextHolder.set(context);
            
            // Log execution start
            execLogger.info(String.format("Starting %s execution: %s", type, name));
            execLogger.saveInput(input);
            execLogger.saveMetadata(context);
            
            logger.info("Executing {} '{}' with execution_id: {}", type, name, executionId);
            
            // Execute
            RunResult result = callable.call();
            
            // Update context based on result
            if (result.getStatus() == ExecutionStatus.SUCCESS) {
                context.markSuccess(result.getOutput());
                execLogger.info("Execution completed successfully");
                execLogger.saveOutput(result.getOutput());
            } else if (result.getStatus() == ExecutionStatus.FAILED) {
                context.markFailed(result.getError());
                execLogger.error("Execution failed: " + result.getError());
            }
            
            // Set execution ID in result
            result.setExecutionId(executionId);
            
            // Copy metadata if available
            if (result.getMetadata() != null) {
                context.setDetails(result.getMetadata().getDetails());
            }
            
            // Save final metadata
            execLogger.saveMetadata(context);
            
            return result;
            
        } catch (IllegalArgumentException e) {
            // Handle invalid artifact names or configurations
            context.markFailed("Invalid artifact: " + e.getMessage());
            logger.error("Execution {} failed - invalid artifact configuration", executionId, e);
            
            ExecutionLogger execLogger = new ExecutionLogger(executionStorage, executionId);
            execLogger.error("Invalid artifact configuration", e);
            execLogger.saveMetadata(context);
            
            RunResult result = RunResult.failed("Invalid artifact: " + e.getMessage());
            result.setExecutionId(executionId);
            return result;
            
        } catch (IOException e) {
            // Handle I/O errors (file system, network, etc.)
            context.markFailed("I/O error: " + e.getMessage());
            logger.error("Execution {} failed with I/O error", executionId, e);
            
            ExecutionLogger execLogger = new ExecutionLogger(executionStorage, executionId);
            execLogger.error("I/O error during execution", e);
            execLogger.saveMetadata(context);
            
            RunResult result = RunResult.failed("I/O error: " + e.getMessage());
            result.setExecutionId(executionId);
            return result;
            
        } catch (RuntimeException e) {
            // Handle unexpected runtime errors
            context.markFailed(e.getMessage());
            logger.error("Execution {} failed with unexpected error", executionId, e);
            
            ExecutionLogger execLogger = new ExecutionLogger(executionStorage, executionId);
            execLogger.error("Unexpected error during execution", e);
            execLogger.saveMetadata(context);
            
            RunResult result = RunResult.failed("Unexpected error: " + e.getMessage());
            result.setExecutionId(executionId);
            return result;
            
        } catch (Exception e) {
            // Handle any other unexpected exceptions
            context.markFailed(e.getMessage());
            logger.error("Execution {} failed with unexpected exception", executionId, e);
            
            try {
                ExecutionLogger execLogger = new ExecutionLogger(executionStorage, executionId);
                execLogger.error("Unexpected error during execution", e);
                execLogger.saveMetadata(context);
            } catch (Exception logError) {
                logger.error("Failed to save error metadata for execution {}", executionId, logError);
            }
            
            RunResult result = RunResult.failed("Unexpected error: " + e.getMessage());
            result.setExecutionId(executionId);
            return result;
        } finally {
            // Clear thread-local context
            ExecutionContextHolder.clear();
        }
    }
    
    @FunctionalInterface
    private interface ExecutionCallable {
        RunResult call() throws Exception;
    }

    public List<String> listAgents() { return artifactRepository.listAgents(); }
    public List<String> listTools() { return artifactRepository.listTools(); }
    public List<String> listPipelines() { return artifactRepository.listPipelines(); }
    public List<String> listSkills() { return artifactRepository.listSkills(); }
    public ArtifactRepository getArtifactRepository() { return artifactRepository; }

    /**
     * Executes a {@link org.hubbers.annotation.Pipeline}-annotated flow object.
     *
     * <p>Discovers all {@link org.hubbers.annotation.Agent} and {@link org.hubbers.annotation.Task}
     * methods on the flow class, registers the agents and the assembled pipeline in the
     * artifact repository, then executes the pipeline.</p>
     *
     * <pre>{@code
     * RunResult result = hubbers.runFlow(new ResearchFlow(), inputsNode);
     * }</pre>
     *
     * @param flow  an instance of a {@code @Pipeline}-annotated class
     * @param input pipeline input as JSON
     * @return the execution result
     * @see org.hubbers.annotation.FlowRunner
     */
    public RunResult runFlow(Object flow, JsonNode input) {
        return new org.hubbers.annotation.FlowRunner(this).run(flow, input);
    }
    
    public ExecutionStorageService getExecutionStorage() { return executionStorage; }
    public org.hubbers.forms.JuiFormService getFormService() { return formService; }
    public PipelineExecutor getPipelineExecutor() { return pipelineExecutor; }

    /**
     * Execute an agent using a pre-configured manifest and conversation ID.
     * The manifest's {@code mode} determines simple vs agentic execution.
     * Used for advanced scenarios where the manifest has been dynamically modified
     * (e.g., artifact injection for the universal task agent).
     *
     * @param manifest Pre-configured agent manifest (tools already injected if needed)
     * @param input Input JSON for the agent
     * @param conversationId Conversation ID for multi-turn dialogue (or null for new conversation)
     * @return RunResult with agent output
     */
    public RunResult executeAgenticAgent(org.hubbers.manifest.agent.AgentManifest manifest, 
                                        JsonNode input, 
                                        String conversationId) {
        return executeWithTracking("agent", manifest.getAgent().getName(), input, () -> {
            // Manifest is already validated and configured by caller
            return agentExecutor.execute(manifest, input, conversationId);
        });
    }

    /**
     * Execute an agent asynchronously, returning a {@link java.util.concurrent.CompletableFuture}.
     *
     * <p>The execution runs on a shared virtual thread executor, freeing the calling
     * thread immediately.</p>
     *
     * @param name the agent name to execute
     * @param input input data for the agent
     * @param conversationId conversation ID (or null)
     * @return a CompletableFuture that completes with the RunResult
     */
    public java.util.concurrent.CompletableFuture<RunResult> runAgentAsync(
            String name, JsonNode input, String conversationId) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> runAgent(name, input, conversationId),
                ASYNC_EXECUTOR
        );
    }

    /**
     * Execute a pipeline asynchronously.
     *
     * @param name the pipeline name
     * @param input input data
     * @return a CompletableFuture that completes with the RunResult
     */
    public java.util.concurrent.CompletableFuture<RunResult> runPipelineAsync(
            String name, JsonNode input) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> runPipeline(name, input),
                ASYNC_EXECUTOR
        );
    }

    /** Shared executor for async operations — uses virtual threads (Java 21). */
    private static final java.util.concurrent.ExecutorService ASYNC_EXECUTOR =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
}
