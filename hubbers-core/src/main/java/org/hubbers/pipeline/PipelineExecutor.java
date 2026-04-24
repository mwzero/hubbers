package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.*;
import org.hubbers.manifest.pipeline.ErrorHandler;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.manifest.pipeline.RetryConfig;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineExecutor {
    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);
    private final ArtifactRepository artifactRepository;
    private final ExecutorRegistry executorRegistry;
    private final ToolExecutor toolExecutor;
    private final InputMapper inputMapper;
    private final ConditionEvaluator conditionEvaluator;
    private PipelineStateStore pipelineStateStore;

    public PipelineExecutor(ArtifactRepository artifactRepository,
                            ExecutorRegistry executorRegistry,
                            ToolExecutor toolExecutor,
                            InputMapper inputMapper) {
        this.artifactRepository = artifactRepository;
        this.executorRegistry = executorRegistry;
        this.toolExecutor = toolExecutor;
        this.inputMapper = inputMapper;
        this.conditionEvaluator = new ConditionEvaluator();
    }

    /**
     * Sets the pipeline state store for pause/resume support.
     *
     * @param stateStore the state store to use for persisting paused pipeline state
     */
    public void setPipelineStateStore(PipelineStateStore stateStore) {
        this.pipelineStateStore = stateStore;
    }

    public RunResult execute(PipelineManifest manifest, JsonNode input) {
        log.info("Executing pipeline: {}", manifest.getPipeline().getName());
        
        // Get execution context if available
        ExecutionContext context = ExecutionContextHolder.get();
        ExecutionLogger execLogger = context != null ? context.getLogger() : null;
        
        // Create execution trace for pipeline
        ExecutionTrace executionTrace = new ExecutionTrace("pipeline");
        
        PipelineState state = new PipelineState();
        int stepNumber = 0;
        int totalSteps = manifest.getSteps().size();
        
        for (PipelineStep step : manifest.getSteps()) {
            String artifactName = step.getTool() != null && !step.getTool().isBlank() ? step.getTool() : step.getAgent();
            String artifactType = step.getTool() != null && !step.getTool().isBlank() ? "tool" : "agent";
            
            log.info("[{}/{}] Executing step '{}' → {} '{}'", 
                stepNumber + 1, totalSteps, step.getId(), artifactType, artifactName);
            
            // Create step trace
            PipelineStepTrace stepTrace = new PipelineStepTrace(stepNumber + 1, step.getId(), artifactType, artifactName);
            long stepStartTime = System.currentTimeMillis();
            stepTrace.setStartTime(stepStartTime);

            // --- Condition evaluation ---
            if (step.getCondition() != null && !step.getCondition().isBlank()) {
                boolean conditionMet = conditionEvaluator.evaluate(step.getCondition(), state, input);
                if (!conditionMet) {
                    log.info("[{}/{}] Step '{}' skipped — condition '{}' evaluated to false",
                            stepNumber + 1, totalSteps, step.getId(), step.getCondition());
                    stepTrace.setEndTime(System.currentTimeMillis());
                    stepTrace.setDurationMs(stepTrace.getEndTime() - stepStartTime);
                    stepTrace.setStatus(ExecutionStatus.SUCCESS);
                    executionTrace.addPipelineStep(stepTrace);
                    if (execLogger != null) {
                        execLogger.info("Step skipped: condition not met");
                    }
                    stepNumber++;
                    continue;
                }
                log.debug("Step '{}' condition '{}' evaluated to true — proceeding", step.getId(), step.getCondition());
            }
            
            // Check if step has a form (human-in-the-loop)
            if (step.getForm() != null) {
                log.info("Step '{}' requires human input - form defined", step.getId());
                
                if (execLogger != null) {
                    execLogger.info("Step has form definition (pause/resume not yet implemented)");
                }
            }
            
            // Create step logger if execution context is available
            ExecutionLogger stepLogger = null;
            if (execLogger != null) {
                stepLogger = execLogger.createStepLogger(stepNumber, step.getId());
                stepLogger.info(String.format("Starting step: %s (%s '%s')", step.getId(), artifactType, artifactName));
            }
            
            JsonNode stepInput = inputMapper.map(input, step.getInputMapping(), state);
            
            // Set step input in trace
            stepTrace.setInput(stepInput);
            
            // Log step input
            if (stepLogger != null) {
                stepLogger.saveInput(stepInput);
            }

            // --- Execute with retry logic ---
            RunResult stepResult = executeStepWithRetry(step, stepInput, stepTrace, stepStartTime,
                    executionTrace, stepLogger, stepNumber, totalSteps);

            // --- Error handling ---
            if (stepResult.getStatus() != ExecutionStatus.SUCCESS) {
                ErrorHandler errorHandler = step.getOnError();
                ErrorHandler.Action action = (errorHandler != null) ? errorHandler.getAction() : ErrorHandler.Action.FAIL;

                switch (action) {
                    case SKIP -> {
                        log.warn("[{}/{}] Step '{}' failed but on_error=SKIP — continuing pipeline",
                                stepNumber + 1, totalSteps, step.getId());
                        stepTrace.setEndTime(System.currentTimeMillis());
                        stepTrace.setDurationMs(stepTrace.getEndTime() - stepStartTime);
                        stepTrace.setStatus(ExecutionStatus.FAILED);
                        stepTrace.setError(stepResult.getError());
                        executionTrace.addPipelineStep(stepTrace);
                        if (stepLogger != null) {
                            stepLogger.error("Step failed (skipped): " + stepResult.getError());
                        }
                        stepNumber++;
                        continue;
                    }
                    case FALLBACK -> {
                        String fallbackStepId = errorHandler.getFallbackStep();
                        if (fallbackStepId == null || fallbackStepId.isBlank()) {
                            log.error("Step '{}' on_error=FALLBACK but no fallback_step defined", step.getId());
                            stepResult.setExecutionTrace(executionTrace);
                            return stepResult;
                        }
                        log.info("[{}/{}] Step '{}' failed — executing fallback step '{}'",
                                stepNumber + 1, totalSteps, step.getId(), fallbackStepId);
                        // Find and execute the fallback step inline
                        PipelineStep fallbackStep = manifest.getSteps().stream()
                                .filter(s -> fallbackStepId.equals(s.getId()))
                                .findFirst()
                                .orElse(null);
                        if (fallbackStep == null) {
                            log.error("Fallback step '{}' not found in pipeline", fallbackStepId);
                            stepResult.setExecutionTrace(executionTrace);
                            return stepResult;
                        }
                        JsonNode fallbackInput = inputMapper.map(input, fallbackStep.getInputMapping(), state);
                        RunResult fallbackResult = executeStepDirect(fallbackStep, fallbackInput);
                        if (fallbackResult.getStatus() == ExecutionStatus.SUCCESS) {
                            state.putStepOutput(step.getId(), fallbackResult.getOutput());
                            stepTrace.setEndTime(System.currentTimeMillis());
                            stepTrace.setDurationMs(stepTrace.getEndTime() - stepStartTime);
                            stepTrace.setStatus(ExecutionStatus.SUCCESS);
                            stepTrace.setOutput(fallbackResult.getOutput());
                            executionTrace.addPipelineStep(stepTrace);
                            stepNumber++;
                            continue;
                        }
                        // Fallback also failed
                        log.error("Fallback step '{}' also failed: {}", fallbackStepId, fallbackResult.getError());
                        fallbackResult.setExecutionTrace(executionTrace);
                        return fallbackResult;
                    }
                    default -> {
                        // FAIL — original behavior: add the failed step trace before returning
                        stepTrace.setEndTime(System.currentTimeMillis());
                        stepTrace.setDurationMs(stepTrace.getEndTime() - stepStartTime);
                        stepTrace.setStatus(ExecutionStatus.FAILED);
                        stepTrace.setError(stepResult.getError());
                        executionTrace.addPipelineStep(stepTrace);
                        if (stepLogger != null) {
                            stepLogger.error("Step failed: " + stepResult.getError());
                        }
                        stepResult.setExecutionTrace(executionTrace);
                        return stepResult;
                    }
                }
            }
            
            // Update step trace with result
            long stepEndTime = System.currentTimeMillis();
            stepTrace.setEndTime(stepEndTime);
            stepTrace.setDurationMs(stepEndTime - stepStartTime);
            stepTrace.setStatus(stepResult.getStatus());
            stepTrace.setOutput(stepResult.getOutput());
            
            // Add successful step to trace
            executionTrace.addPipelineStep(stepTrace);
            
            log.info("[{}/{}] Step '{}' completed successfully", stepNumber + 1, totalSteps, step.getId());
            
            // Log step output
            if (stepLogger != null) {
                stepLogger.info("Step completed successfully");
                stepLogger.saveOutput(stepResult.getOutput());
            }
            
            state.putStepOutput(step.getId(), stepResult.getOutput());
            stepNumber++;
        }
        
        log.info("Pipeline '{}' completed successfully", manifest.getPipeline().getName());
        
        if (execLogger != null) {
            execLogger.info("Pipeline completed successfully - all steps executed");
        }
        
        String lastStepId = manifest.getSteps().get(manifest.getSteps().size() - 1).getId();
        RunResult result = RunResult.success(state.getStepOutput(lastStepId));
        result.setExecutionTrace(executionTrace);
        return result;
    }

    /**
     * Resumes a paused pipeline execution from the point it was paused.
     *
     * <p>Loads the saved state from the pipeline state store, restores the step
     * outputs, and continues execution from the paused step with the provided
     * form data merged into the input.</p>
     *
     * @param executionId the execution ID of the paused pipeline
     * @param formData    the form data submitted by the user
     * @return the result of the resumed pipeline execution
     * @throws IllegalStateException if no state store is configured or execution not found
     */
    public RunResult resume(String executionId, JsonNode formData) {
        if (pipelineStateStore == null) {
            throw new IllegalStateException("Pipeline state store not configured — cannot resume");
        }
        PipelineExecutionSnapshot snapshot = pipelineStateStore.load(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No paused execution found for ID: " + executionId));

        log.info("Resuming pipeline '{}' from step '{}' (index {})",
                snapshot.getPipelineName(), snapshot.getPausedAtStepId(), snapshot.getPausedAtStepIndex());

        // Reload the manifest
        var manifest = artifactRepository.loadPipeline(snapshot.getPipelineName());

        // Restore pipeline state
        PipelineState state = new PipelineState();
        state.restoreOutputs(snapshot.getCompletedStepOutputs());

        // Merge form data with original input
        JsonNode input = snapshot.getOriginalInput();

        // Execute remaining steps starting from the paused step
        ExecutionTrace executionTrace = new ExecutionTrace("pipeline");
        int startIndex = snapshot.getPausedAtStepIndex();
        int totalSteps = manifest.getSteps().size();

        for (int i = startIndex; i < totalSteps; i++) {
            PipelineStep step = manifest.getSteps().get(i);
            JsonNode stepInput = inputMapper.map(input, step.getInputMapping(), state);

            // For the paused step, merge form data
            if (i == startIndex && formData != null) {
                var merged = input.deepCopy();
                if (merged.isObject() && formData.isObject()) {
                    formData.fields().forEachRemaining(f ->
                            ((com.fasterxml.jackson.databind.node.ObjectNode) merged).set(f.getKey(), f.getValue()));
                }
                stepInput = inputMapper.map(merged, step.getInputMapping(), state);
            }

            RunResult stepResult = executeStepWithRetry(step, stepInput, 
                    new PipelineStepTrace(i + 1, step.getId(), 
                            step.getTool() != null ? "tool" : "agent",
                            step.getTool() != null ? step.getTool() : step.getAgent()),
                    System.currentTimeMillis(), executionTrace, null, i, totalSteps);

            if (stepResult.getStatus() != ExecutionStatus.SUCCESS) {
                pipelineStateStore.delete(executionId);
                stepResult.setExecutionTrace(executionTrace);
                return stepResult;
            }
            state.putStepOutput(step.getId(), stepResult.getOutput());
        }

        // Clean up saved state
        pipelineStateStore.delete(executionId);

        String lastStepId = manifest.getSteps().get(totalSteps - 1).getId();
        RunResult result = RunResult.success(state.getStepOutput(lastStepId));
        result.setExecutionTrace(executionTrace);
        return result;
    }

    /**
     * Executes a step with optional retry logic.
     * If a {@link RetryConfig} is present on the step, retries up to {@code maxRetries} times
     * with {@code backoffMs} delay between attempts.
     */
    private RunResult executeStepWithRetry(PipelineStep step, JsonNode stepInput,
                                            PipelineStepTrace stepTrace, long stepStartTime,
                                            ExecutionTrace executionTrace,
                                            ExecutionLogger stepLogger,
                                            int stepNumber, int totalSteps) {
        RetryConfig retryConfig = step.getRetry();
        int maxAttempts = (retryConfig != null) ? retryConfig.getMaxRetries() + 1 : 1;
        long backoffMs = (retryConfig != null) ? retryConfig.getBackoffMs() : 0;

        RunResult stepResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                stepResult = executeStepDirect(step, stepInput);
            } catch (Exception e) {
                String errorMsg = "Step execution failed: " + e.getMessage();
                log.error("Step '{}' attempt {}/{} failed with exception", step.getId(), attempt, maxAttempts, e);
                stepResult = RunResult.failed(errorMsg);
            }

            if (stepResult.getStatus() == ExecutionStatus.SUCCESS) {
                return stepResult;
            }

            if (attempt < maxAttempts) {
                log.warn("Step '{}' attempt {}/{} failed — retrying in {}ms",
                        step.getId(), attempt, maxAttempts, backoffMs);
                if (stepLogger != null) {
                    stepLogger.error(String.format("Attempt %d/%d failed: %s — retrying",
                            attempt, maxAttempts, stepResult.getError()));
                }
                if (backoffMs > 0) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return RunResult.failed("Retry interrupted");
                    }
                }
            } else {
                log.error("Step '{}' failed after {} attempt(s): {}",
                        step.getId(), maxAttempts, stepResult.getError());
                if (stepLogger != null) {
                    stepLogger.error("Step failed after " + maxAttempts + " attempt(s): " + stepResult.getError());
                }
            }
        }
        return stepResult;
    }

    /**
     * Executes a single step directly (no retry, no condition check).
     */
    private RunResult executeStepDirect(PipelineStep step, JsonNode stepInput) {
        if (step.getTool() != null && !step.getTool().isBlank()) {
            return toolExecutor.execute(artifactRepository.loadTool(step.getTool()), stepInput);
        } else {
            return executorRegistry.executeAgent(
                    artifactRepository.loadAgent(step.getAgent()), stepInput, null);
        }
    }
}
