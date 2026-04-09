package org.hubbers.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hubbers.agent.AgenticExecutor;
import org.hubbers.execution.*;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.nlp.NaturalLanguageTaskService;
import org.hubbers.nlp.TaskExecutionResult;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.skill.SkillExecutor;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RuntimeFacade {
    
    private static final Logger logger = LoggerFactory.getLogger(RuntimeFacade.class);
    
    private final ArtifactRepository artifactRepository;
    private final AgenticExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final PipelineExecutor pipelineExecutor;
    private final SkillExecutor skillExecutor;
    private final ManifestValidator manifestValidator;
    private final ExecutionStorageService executionStorage;
    private final org.hubbers.forms.JuiFormService formService;
    
    // Lazy-initialized for natural language task execution
    private NaturalLanguageTaskService taskService;

    public RuntimeFacade(ArtifactRepository artifactRepository,
                         AgenticExecutor agentExecutor,
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

    /**
     * Execute an agent with optional conversation support and intelligent mode detection.
     * 
     * If input contains {"request": "natural language string"}, automatically routes through
     * NaturalLanguageTaskService for enhanced orchestration with dynamic tool injection.
     * Otherwise, executes the agent directly with its configured tools.
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
        // Detect if input is a natural language task request
        boolean isNaturalLanguageMode = isNaturalLanguageRequest(input);
        
        if (isNaturalLanguageMode) {
            // Natural language task execution mode
            logger.info("Detected natural language request for agent '{}', routing through task service", name);
            return executeNaturalLanguageTask(name, input, conversationId);
        } else {
            // Direct agent execution mode
            return executeWithTracking("agent", name, input, () -> {
                var manifest = artifactRepository.loadAgent(name);
                var validation = manifestValidator.validateAgent(manifest);
                if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
                return agentExecutor.execute(manifest, input, conversationId);
            });
        }
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
     * Execute agent via NaturalLanguageTaskService for enhanced orchestration.
     */
    private RunResult executeNaturalLanguageTask(String name, JsonNode input, String conversationId) {
        try {
            // Lazy-initialize task service
            if (taskService == null) {
                ObjectMapper mapper = JacksonFactory.jsonMapper();
                taskService = new NaturalLanguageTaskService(this, mapper);
            }
            
            // Extract request and context from input
            String request = input.get("request").asText();
            JsonNode context = input.has("context") ? input.get("context") : null;
            
            // Execute via task service
            TaskExecutionResult taskResult;
            if (conversationId != null && !conversationId.isEmpty()) {
                taskResult = taskService.executeTaskWithConversation(request, context, conversationId);
            } else {
                taskResult = taskService.executeTask(request, context);
            }
            
            // Convert TaskExecutionResult to RunResult
            if (taskResult.isSuccess()) {
                RunResult result = RunResult.success(taskResult.getResult());
                result.setMetadata(taskResult.getMetadata());
                return result;
            } else {
                return RunResult.failed(taskResult.getError());
            }
            
        } catch (Exception e) {
            logger.error("Natural language task execution failed for agent '{}'", name, e);
            return RunResult.failed("Task execution failed: " + e.getMessage());
        }
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
            
            logger.info("Execution {} completed with status: {} (duration: {}ms)", 
                executionId, result.getStatus(), context.getDurationMs());
            
            return result;
            
        } catch (Exception e) {
            // Handle unexpected errors
            context.markFailed(e.getMessage());
            logger.error("Execution {} failed with exception", executionId, e);
            
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
    
    public ExecutionStorageService getExecutionStorage() { return executionStorage; }
    public org.hubbers.forms.JuiFormService getFormService() { return formService; }

    /**
     * Execute an agent with ReAct loop using a pre-configured manifest and conversation ID.
     * This is used for advanced scenarios where the agent manifest has been dynamically modified
     * (e.g., tool injection for universal task agent).
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
}
