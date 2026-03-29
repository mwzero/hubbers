package org.hubbers.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgentExecutor;
import org.hubbers.artifact.ArtifactRepository;
import org.hubbers.execution.RunResult;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.validation.ManifestValidator;

import java.util.List;

public class RuntimeFacade {
    private final ArtifactRepository artifactRepository;
    private final AgentExecutor agentExecutor;
    private final ToolExecutor toolExecutor;
    private final PipelineExecutor pipelineExecutor;
    private final ManifestValidator manifestValidator;

    public RuntimeFacade(ArtifactRepository artifactRepository,
                         AgentExecutor agentExecutor,
                         ToolExecutor toolExecutor,
                         PipelineExecutor pipelineExecutor,
                         ManifestValidator manifestValidator) {
        this.artifactRepository = artifactRepository;
        this.agentExecutor = agentExecutor;
        this.toolExecutor = toolExecutor;
        this.pipelineExecutor = pipelineExecutor;
        this.manifestValidator = manifestValidator;
    }

    public RunResult runAgent(String name, JsonNode input) {
        var manifest = artifactRepository.loadAgent(name);
        var validation = manifestValidator.validateAgent(manifest);
        if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
        return agentExecutor.execute(manifest, input);
    }

    public RunResult runTool(String name, JsonNode input) {
        var manifest = artifactRepository.loadTool(name);
        var validation = manifestValidator.validateTool(manifest);
        if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
        return toolExecutor.execute(manifest, input);
    }

    public RunResult runPipeline(String name, JsonNode input) {
        var manifest = artifactRepository.loadPipeline(name);
        var validation = manifestValidator.validatePipeline(manifest);
        if (!validation.isValid()) return RunResult.failed(String.join(", ", validation.getErrors()));
        return pipelineExecutor.execute(manifest, input);
    }

    public List<String> listAgents() { return artifactRepository.listAgents(); }
    public List<String> listTools() { return artifactRepository.listTools(); }
    public List<String> listPipelines() { return artifactRepository.listPipelines(); }
}
