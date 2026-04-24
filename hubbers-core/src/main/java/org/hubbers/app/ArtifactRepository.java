package org.hubbers.app;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.hubbers.skill.SkillMdParser;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Stream;

@Slf4j
public class ArtifactRepository {

    private final Path repoRoot;
    private final ObjectMapper yamlMapper = JacksonFactory.yamlMapper();
    private final SkillMdParser skillParser = new SkillMdParser();
    
    // Progressive disclosure: cache lightweight metadata, load full manifest on demand
    private final Map<String, SkillMetadata> skillMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, AgentManifest> agentCache = new ConcurrentHashMap<>();
    private final Map<String, ToolManifest> toolCache = new ConcurrentHashMap<>();
    private final Map<String, PipelineManifest> pipelineCache = new ConcurrentHashMap<>();


    public ArtifactRepository(Path repoRoot) {
        this.repoRoot = repoRoot;
        listManifestNames(repoRoot, "tools", "tool.yaml");
    }

    public java.util.List<String> listAgents() {
        return Stream.concat(
                listManifestNames(repoRoot, "agents", "agent.yaml").stream(),
                agentCache.keySet().stream())
            .distinct()
            .sorted()
            .toList();
    }

    public java.util.List<String> listTools() {
        return Stream.concat(
                listManifestNames(repoRoot, "tools", "tool.yaml").stream(),
                toolCache.keySet().stream())
            .distinct()
            .sorted()
            .toList();
    }

     public java.util.List<String> listPipelines() {
        return Stream.concat(
                listManifestNames(repoRoot, "pipelines", "pipeline.yaml").stream(),
                pipelineCache.keySet().stream())
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Load an agent manifest from agent.yaml.
     *
     * @param name Agent name
     * @return Parsed AgentManifest
     */
    public AgentManifest loadAgent(String name) {
        AgentManifest cached = agentCache.get(name);
        if (cached != null) {
            return cached;
        }

        Path agentYamlPath = repoRoot.resolve("agents").resolve(name).resolve("agent.yaml");
        if (Files.exists(agentYamlPath)) {
            log.debug("Loading agent {} from agent.yaml", name);
            return read(agentYamlPath, AgentManifest.class);
        }

        throw new IllegalStateException(
            "Agent '" + name + "' not found. Expected agent.yaml in: " + agentYamlPath.getParent()
        );
    }

    public ToolManifest loadTool(String name) {
        ToolManifest cached = toolCache.get(name);
        if (cached != null) {
            return cached;
        }

        return read(repoRoot.resolve("tools").resolve(name).resolve("tool.yaml"), ToolManifest.class);
    }

    public PipelineManifest loadPipeline(String name) {
        PipelineManifest cached = pipelineCache.get(name);
        if (cached != null) {
            return cached;
        }

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

    public void addAgent(AgentManifest researcher) {
        if (researcher == null || researcher.getAgent() == null || researcher.getAgent().getName() == null
                || researcher.getAgent().getName().isBlank()) {
            throw new IllegalArgumentException("Agent manifest must define a non-blank agent name");
        }

        agentCache.put(researcher.getAgent().getName(), researcher);
    }

    public void addTool(ToolManifest tool) {
        if (tool == null || tool.getTool() == null || tool.getTool().getName() == null
                || tool.getTool().getName().isBlank()) {
            throw new IllegalArgumentException("Tool manifest must define a non-blank tool name");
        }

        toolCache.put(tool.getTool().getName(), tool);
    }

     public void addPipeline(PipelineManifest pipeline) {
        if (pipeline == null || pipeline.getPipeline() == null || pipeline.getPipeline().getName() == null
                || pipeline.getPipeline().getName().isBlank()) {
            throw new IllegalArgumentException("Pipeline manifest must define a non-blank pipeline name");
        }

        pipelineCache.put(pipeline.getPipeline().getName(), pipeline);
    }

    
}
