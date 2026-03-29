package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgentExecutor;
import org.hubbers.artifact.ArtifactRepository;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.tool.ToolExecutor;

public class PipelineExecutor {
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
        PipelineState state = new PipelineState();
        for (PipelineStep step : manifest.getSteps()) {
            JsonNode stepInput = inputMapper.map(input, step.getInputMapping(), state);
            RunResult stepResult;
            if (step.getTool() != null && !step.getTool().isBlank()) {
                stepResult = toolExecutor.execute(artifactRepository.loadTool(step.getTool()), stepInput);
            } else {
                stepResult = agentExecutor.execute(artifactRepository.loadAgent(step.getAgent()), stepInput);
            }
            if (stepResult.getStatus() != org.hubbers.execution.ExecutionStatus.SUCCESS) {
                return stepResult;
            }
            state.putStepOutput(step.getId(), stepResult.getOutput());
        }
        String lastStepId = manifest.getSteps().get(manifest.getSteps().size() - 1).getId();
        return RunResult.success(state.getStepOutput(lastStepId));
    }
}
