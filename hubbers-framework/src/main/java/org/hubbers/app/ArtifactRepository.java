package org.hubbers.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.agent.AgentMdParser;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.hubbers.skill.SkillMdParser;
import org.hubbers.util.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Stream;

public class ArtifactRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtifactRepository.class);

    private final Path repoRoot;
    private final ObjectMapper yamlMapper = JacksonFactory.yamlMapper();
    private final SkillMdParser skillParser = new SkillMdParser();
    private final AgentMdParser agentMdParser = new AgentMdParser();
    
    // Progressive disclosure: cache lightweight metadata, load full manifest on demand
    private final Map<String, SkillMetadata> skillMetadataCache = new ConcurrentHashMap<>();

    public ArtifactRepository(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public java.util.List<String> listAgents() {
        // List agents with either agent.yaml or AGENT.md
        return listManifestNamesMultiFormat(repoRoot, "agents", "agent.yaml", "AGENT.md");
    }

    public java.util.List<String> listTools() {
        return listManifestNames(repoRoot, "tools", "tool.yaml");
    }

    public java.util.List<String> listPipelines() {
        return listManifestNames(repoRoot, "pipelines", "pipeline.yaml");
    }

    /**
     * Load an agent manifest.
     * Supports both formats: AGENT.md (priority) and agent.yaml (fallback).
     * 
     * @param name Agent name
     * @return Parsed AgentManifest
     */
    public AgentManifest loadAgent(String name) {
        Path agentDir = repoRoot.resolve("agents").resolve(name);
        Path agentMdPath = agentDir.resolve("AGENT.md");
        Path agentYamlPath = agentDir.resolve("agent.yaml");
        
        // Prioritize AGENT.md if it exists
        if (Files.exists(agentMdPath)) {
            try {
                log.debug("Loading agent {} from AGENT.md", name);
                return agentMdParser.parse(agentDir);
            } catch (IOException e) {
                log.error("Failed to parse AGENT.md for {}, falling back to agent.yaml", name, e);
                // Fall through to YAML if MD parsing fails
            }
        }
        
        // Fall back to agent.yaml
        if (Files.exists(agentYamlPath)) {
            log.debug("Loading agent {} from agent.yaml", name);
            return read(agentYamlPath, AgentManifest.class);
        }
        
        throw new IllegalStateException(
            "Agent '" + name + "' not found. Expected AGENT.md or agent.yaml in: " + agentDir
        );
    }

    public ToolManifest loadTool(String name) {
        return read(repoRoot.resolve("tools").resolve(name).resolve("tool.yaml"), ToolManifest.class);
    }

    public PipelineManifest loadPipeline(String name) {
        return read(repoRoot.resolve("pipelines").resolve(name).resolve("pipeline.yaml"), PipelineManifest.class);
    }

    /**
     * List all available skills (scans repo/skills/ for SKILL.md files).
     * Returns skill names matching directory names.
     */
    public List<String> listSkills() {
        return listManifestNames(repoRoot, "skills", "SKILL.md");
    }

    /**
     * Get lightweight skill metadata (progressive disclosure).
     * Cached for performance - only name, description, execution_mode loaded.
     * 
     * @param name Skill name
     * @return SkillMetadata or null if skill not found
     */
    public SkillMetadata getSkillMetadata(String name) {
        return skillMetadataCache.computeIfAbsent(name, this::loadSkillMetadata);
    }

    /**
     * Load full skill manifest (SKILL.md body + optional directories).
     * Only called when skill is actually needed (progressive disclosure).
     * 
     * @param name Skill name
     * @return Full SkillManifest
     */
    public SkillManifest loadSkill(String name) {
        Path skillPath = repoRoot.resolve("skills").resolve(name);
        try {
            return skillParser.parse(skillPath);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load skill: " + name, e);
        }
    }

    /**
     * Get all skill metadata (for catalog injection).
     * Uses cached metadata for performance.
     */
    public List<SkillMetadata> getAllSkillMetadata() {
        return listSkills().stream()
            .map(this::getSkillMetadata)
            .filter(meta -> meta != null)
            .toList();
    }

    /**
     * Load only the metadata for a skill (internal helper).
     */
    private SkillMetadata loadSkillMetadata(String name) {
        Path skillPath = repoRoot.resolve("skills").resolve(name);
        try {
            return skillParser.parseMetadata(skillPath);
        } catch (IOException e) {
            log.error("Failed to load skill metadata: {}", name, e);
            return null;
        }
    }

    /**
     * List manifest names supporting multiple file formats.
     * Returns directories that contain at least one of the specified manifest files.
     * 
     * @param root Root path
     * @param folder Subfolder name
     * @param manifestNames Multiple possible manifest file names
     * @return List of manifest names
     */
    private List<String> listManifestNamesMultiFormat(Path root, String folder, String... manifestNames) {
        Path base = root.resolve(folder);
        if (!Files.exists(base)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(base)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        // Check if any of the manifest files exist
                        for (String manifestName : manifestNames) {
                            if (Files.exists(p.resolve(manifestName))) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan artifacts in " + base, e);
        }
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
