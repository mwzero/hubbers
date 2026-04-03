package org.hubbers.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgenticExecutor;
import org.hubbers.execution.*;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.tool.ToolExecutor;
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
    private final ManifestValidator manifestValidator;
    private final ExecutionStorageService executionStorage;
    private final org.hubbers.forms.JuiFormService formService;

    public RuntimeFacade(ArtifactRepository artifactRepository,
                         AgenticExecutor agentExecutor,
                         ToolExecutor toolExecutor,
                         PipelineExecutor pipelineExecutor,
                         ManifestValidator manifestValidator,
                         ExecutionStorageService executionStorage,
                         org.hubbers.forms.JuiFormService formService) {
        this.artifactRepository = artifactRepository;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.pipelineExecutor = pipelineExecutor;
        this.manifestValidator = manifestValidator;
        this.executionStorage = executionStorage;
        this.formService = formService;
    }

    public RunResult runAgent(String name, JsonNode input) {
        return executeWithTracking("agent", name, input, () -> {
            var manifest = artifactRepository.loadAgent(name);
            var validation = manifestValidator.validateAgent(manifest);
            if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
            return agentExecutor.execute(manifest, input, null);
        });
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
    
    public ExecutionStorageService getExecutionStorage() { return executionStorage; }
    public org.hubbers.forms.JuiFormService getFormService() { return formService; }
    public ArtifactRepository getArtifactRepository() { return artifactRepository; }
}
