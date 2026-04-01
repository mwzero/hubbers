package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgentExecutor;
import org.hubbers.artifact.ArtifactRepository;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineExecutor {
    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);
    private final ArtifactRepository artifactRepository;
    private final AgentExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final InputMapper inputMapper;

    public PipelineExecutor(ArtifactRepository artifactRepository,
                            AgentExecutor agentExecutor,
                            ToolExecutor toolExecutor,
                            InputMapper inputMapper) {
        this.artifactRepository = artifactRepository;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.inputMapper = inputMapper;
    }

    public RunResult execute(PipelineManifest manifest, JsonNode input) {
        log.info("Executing pipeline: {}", manifest.getPipeline().getName());
        PipelineState state = new PipelineState();
        int stepNumber = 1;
        int totalSteps = manifest.getSteps().size();
        
        for (PipelineStep step : manifest.getSteps()) {
            String artifactName = step.getTool() != null && !step.getTool().isBlank() ? step.getTool() : step.getAgent();
            String artifactType = step.getTool() != null && !step.getTool().isBlank() ? "tool" : "agent";
            
            log.info("[{}/{}] Executing step '{}' → {} '{}'", 
                stepNumber, totalSteps, step.getId(), artifactType, artifactName);
            
            JsonNode stepInput = inputMapper.map(input, step.getInputMapping(), state);
            RunResult stepResult;
            
            if (step.getTool() != null && !step.getTool().isBlank()) {
                stepResult = toolExecutor.execute(artifactRepository.loadTool(step.getTool()), stepInput);
            } else {
                stepResult = agentExecutor.execute(artifactRepository.loadAgent(step.getAgent()), stepInput);
            }
            
            if (stepResult.getStatus() != org.hubbers.execution.ExecutionStatus.SUCCESS) {
                log.error("Step '{}' failed: {}", step.getId(), stepResult.getError());
                return stepResult;
            }
            
            log.info("[{}/{}] Step '{}' completed successfully", stepNumber, totalSteps, step.getId());
            state.putStepOutput(step.getId(), stepResult.getOutput());
            stepNumber++;
        }
        
        log.info("Pipeline '{}' completed successfully", manifest.getPipeline().getName());
        String lastStepId = manifest.getSteps().get(manifest.getSteps().size() - 1).getId();
        return RunResult.success(state.getStepOutput(lastStepId));
    }
}
