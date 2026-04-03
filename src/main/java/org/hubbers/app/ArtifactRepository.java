package org.hubbers.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import java.util.stream.Stream;

public class ArtifactRepository {

    private final Path repoRoot;
    private final ObjectMapper yamlMapper = JacksonFactory.yamlMapper();

    public ArtifactRepository(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public java.util.List<String> listAgents() {
        return listManifestNames(repoRoot, "agents", "agent.yaml");
    }

    public java.util.List<String> listTools() {
        return listManifestNames(repoRoot, "tools", "tool.yaml");
    }

    public java.util.List<String> listPipelines() {
        return listManifestNames(repoRoot, "pipelines", "pipeline.yaml");
    }

    public AgentManifest loadAgent(String name) {
        return read(repoRoot.resolve("agents").resolve(name).resolve("agent.yaml"), AgentManifest.class);
    }

    public ToolManifest loadTool(String name) {
        return read(repoRoot.resolve("tools").resolve(name).resolve("tool.yaml"), ToolManifest.class);
    }

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

    private List<String> listManifestNames(Path root, String folder, String manifestName) {
        Path base = root.resolve(folder);
        if (!Files.exists(base)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(base)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(manifestName)))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan artifacts in " + base, e);
        }
    }
}
