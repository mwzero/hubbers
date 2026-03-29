package org.hubbers.artifact;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;

import java.util.List;

public interface ArtifactRepository {
    List<String> listAgents();
    List<String> listTools();
    List<String> listPipelines();

    AgentManifest loadAgent(String name);
    ToolManifest loadTool(String name);
    PipelineManifest loadPipeline(String name);
}
