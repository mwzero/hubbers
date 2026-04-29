package org.hubbers.mcp;

import lombok.extern.slf4j.Slf4j;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.mcp.protocol.McpResourceInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bridges the Hubbers ArtifactRepository to MCP resource definitions.
 * Exposes artifact manifests as MCP resources that external chatbots
 * (Claude Desktop, Open WebUI) can browse and read.
 *
 * <p>Resource URI scheme: {@code hubbers://{type}/{name}/manifest}
 * <ul>
 *   <li>{@code hubbers://agents/universal.task/manifest}</li>
 *   <li>{@code hubbers://tools/web.search/manifest}</li>
 *   <li>{@code hubbers://pipelines/file.backup/manifest}</li>
 *   <li>{@code hubbers://skills/code.review/manifest}</li>
 * </ul>
 *
 * @since 0.1.0
 */
@Slf4j
public class McpResourceProvider {

    private final ArtifactRepository artifactRepository;
    private final Path repoRoot;

    /**
     * Creates a new McpResourceProvider.
     *
     * @param artifactRepository the artifact repository to query
     * @param repoRoot           the root path of the artifact repository
     */
    public McpResourceProvider(ArtifactRepository artifactRepository, Path repoRoot) {
        this.artifactRepository = artifactRepository;
        this.repoRoot = repoRoot;
    }

    /**
     * Lists all available MCP resources.
     *
     * @return list of resource descriptors for all artifacts
     */
    public List<McpResourceInfo> listResources() {
        List<McpResourceInfo> resources = new ArrayList<>();

        for (String name : artifactRepository.listAgents()) {
            resources.add(McpResourceInfo.builder()
                    .uri("hubbers://agents/" + name + "/manifest")
                    .name("Agent: " + name)
                    .mimeType("text/yaml")
                    .build());
        }

        for (String name : artifactRepository.listTools()) {
            resources.add(McpResourceInfo.builder()
                    .uri("hubbers://tools/" + name + "/manifest")
                    .name("Tool: " + name)
                    .mimeType("text/yaml")
                    .build());
        }

        for (String name : artifactRepository.listPipelines()) {
            resources.add(McpResourceInfo.builder()
                    .uri("hubbers://pipelines/" + name + "/manifest")
                    .name("Pipeline: " + name)
                    .mimeType("text/yaml")
                    .build());
        }

        for (String name : artifactRepository.listSkills()) {
            resources.add(McpResourceInfo.builder()
                    .uri("hubbers://skills/" + name + "/manifest")
                    .name("Skill: " + name)
                    .mimeType("text/markdown")
                    .build());
        }

        log.debug("Listed {} MCP resources", resources.size());
        return resources;
    }

    /**
     * Reads the content of a specific resource by URI.
     *
     * @param uri the resource URI (e.g. "hubbers://agents/universal.task/manifest")
     * @return the resource content text, or empty if not found
     */
    public Optional<String> readResource(String uri) {
        if (uri == null || !uri.startsWith("hubbers://")) {
            return Optional.empty();
        }

        String path = uri.substring("hubbers://".length());
        String[] parts = path.split("/", 3);
        if (parts.length < 2) {
            return Optional.empty();
        }

        String type = parts[0];
        String name = parts[1];

        return switch (type) {
            case "agents" -> readFile(repoRoot.resolve("agents").resolve(name).resolve("agent.yaml"));
            case "tools" -> readFile(repoRoot.resolve("tools").resolve(name).resolve("tool.yaml"));
            case "pipelines" -> readFile(repoRoot.resolve("pipelines").resolve(name).resolve("pipeline.yaml"));
            case "skills" -> readFile(repoRoot.resolve("skills").resolve(name).resolve("SKILL.md"));
            default -> {
                log.warn("Unknown resource type: {}", type);
                yield Optional.empty();
            }
        };
    }

    private Optional<String> readFile(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                return Optional.of(Files.readString(filePath));
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("Failed to read resource file: {}", filePath, e);
            return Optional.empty();
        }
    }
}
