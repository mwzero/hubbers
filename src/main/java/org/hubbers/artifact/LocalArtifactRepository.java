package org.hubbers.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.nio.file.Path;

public class LocalArtifactRepository implements ArtifactRepository {
    private final Path repoRoot;
    private final ArtifactScanner scanner;
    private final ObjectMapper yamlMapper = JacksonFactory.yamlMapper();

    public LocalArtifactRepository(Path repoRoot, ArtifactScanner scanner) {
        this.repoRoot = repoRoot;
        this.scanner = scanner;
    }

    @Override
    public java.util.List<String> listAgents() {
        return scanner.listManifestNames(repoRoot, "agents", "agent.yaml");
    }

    @Override
    public java.util.List<String> listTools() {
        return scanner.listManifestNames(repoRoot, "tools", "tool.yaml");
    }

    @Override
    public java.util.List<String> listPipelines() {
        return scanner.listManifestNames(repoRoot, "pipelines", "pipeline.yaml");
    }

    @Override
    public AgentManifest loadAgent(String name) {
        return read(repoRoot.resolve("agents").resolve(name).resolve("agent.yaml"), AgentManifest.class);
    }

    @Override
    public ToolManifest loadTool(String name) {
        return read(repoRoot.resolve("tools").resolve(name).resolve("tool.yaml"), ToolManifest.class);
    }

    @Override
    public PipelineManifest loadPipeline(String name) {
        return read(repoRoot.resolve("pipelines").resolve(name).resolve("pipeline.yaml"), PipelineManifest.class);
    }

    private <T> T read(Path path, Class<T> type) {
        try {
            return yamlMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read manifest " + path, e);
        }
    }
}
