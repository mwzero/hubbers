package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgenticExecutor;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.ExecutionContext;
import org.hubbers.execution.ExecutionContextHolder;
import org.hubbers.execution.ExecutionLogger;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineExecutor {
    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);
    private final ArtifactRepository artifactRepository;
    private final AgenticExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final InputMapper inputMapper;

    public PipelineExecutor(ArtifactRepository artifactRepository,
                            AgenticExecutor agentExecutor,
                            ToolExecutor toolExecutor,
                            InputMapper inputMapper) {
        this.artifactRepository = artifactRepository;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.inputMapper = inputMapper;
    }

    public RunResult execute(PipelineManifest manifest, JsonNode input) {
        log.info("Executing pipeline: {}", manifest.getPipeline().getName());
        
        // Get execution context if available
        ExecutionContext context = ExecutionContextHolder.get();
        ExecutionLogger execLogger = context != null ? context.getLogger() : null;
        
        PipelineState state = new PipelineState();
        int stepNumber = 0;
        int totalSteps = manifest.getSteps().size();
        
        for (PipelineStep step : manifest.getSteps()) {
            String artifactName = step.getTool() != null && !step.getTool().isBlank() ? step.getTool() : step.getAgent();
            String artifactType = step.getTool() != null && !step.getTool().isBlank() ? "tool" : "agent";
            
            log.info("[{}/{}] Executing step '{}' → {} '{}'", 
                stepNumber + 1, totalSteps, step.getId(), artifactType, artifactName);
            
            // Check if step has a form (human-in-the-loop)
            if (step.getForm() != null) {
                log.info("Step '{}' requires human input - form defined", step.getId());
                
                // Note: Full pause/resume would require:
                // 1. Persisting PipelineState to disk
                // 2. Creating a form session with pipeline context
                // 3. Returning PAUSED status with session ID
                // 4. Implementing resume endpoint that loads state and continues
                //
                // For now, we log this as a future enhancement.
                // Steps with forms will be executed without pause in this version.
                
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
            
            // Log step input
            if (stepLogger != null) {
                stepLogger.saveInput(stepInput);
            }
            
            RunResult stepResult;
            
            try {
                if (step.getTool() != null && !step.getTool().isBlank()) {
                    stepResult = toolExecutor.execute(artifactRepository.loadTool(step.getTool()), stepInput);
                } else {
                    stepResult = agentExecutor.execute(artifactRepository.loadAgent(step.getAgent()), stepInput, null);
                }
            } catch (Exception e) {
                String errorMsg = "Step execution failed: " + e.getMessage();
                log.error("Step '{}' failed with exception", step.getId(), e);
                
                if (stepLogger != null) {
                    stepLogger.error(errorMsg, e);
                }
                
                return RunResult.failed(errorMsg);
            }
            
            if (stepResult.getStatus() != org.hubbers.execution.ExecutionStatus.SUCCESS) {
                log.error("Step '{}' failed: {}", step.getId(), stepResult.getError());
                
                if (stepLogger != null) {
                    stepLogger.error("Step failed: " + stepResult.getError());
                }
                
                return stepResult;
            }
            
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
        return RunResult.success(state.getStepOutput(lastStepId));
    }
}
