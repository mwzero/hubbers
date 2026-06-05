package org.hubbers.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.agent.ArtifactToFunctionConverter;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.react.artifact.ArtifactResolver;
import org.hubbers.react.model.FunctionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Platform implementation of {@link ArtifactResolver}.
 *
 * <p>Resolves artifact names to {@link FunctionDefinition}s by scanning the
 * {@link ArtifactRepository} and converting manifests via
 * {@link ArtifactToFunctionConverter}.</p>
 *
 * <p>The resolution order for each name is:
 * <ol>
 *   <li>Tool manifest</li>
 *   <li>Agent manifest</li>
 *   <li>Pipeline manifest</li>
 *   <li>Skill metadata</li>
 * </ol>
 * Unknown names are silently skipped (a warning is logged).</p>
 */
public class ArtifactRepositoryResolver implements ArtifactResolver {

    private static final Logger log = LoggerFactory.getLogger(ArtifactRepositoryResolver.class);

    private final ArtifactRepository repository;
    private final ArtifactToFunctionConverter converter;

    /**
     * Creates a new resolver backed by the given repository and converter.
     *
     * @param repository artifact repository
     * @param mapper     Jackson ObjectMapper used by the converter
     */
    public ArtifactRepositoryResolver(ArtifactRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.converter = new ArtifactToFunctionConverter(mapper);
    }

    @Override
    public List<FunctionDefinition> resolve(List<String> artifactNames) {
        List<FunctionDefinition> definitions = new ArrayList<>();
        for (String name : artifactNames) {
            resolveSingle(name).ifPresentOrElse(
                    definitions::add,
                    () -> log.debug("Artifact '{}' not found as tool, agent, pipeline, or skill — skipping", name));
        }
        return definitions;
    }

    @Override
    public Optional<FunctionDefinition> resolveSingle(String artifactName) {
        try {
            ToolManifest tool = repository.loadTool(artifactName);
            if (tool != null) return Optional.of(converter.convertTool(tool));
        } catch (Exception ignored) {}

        try {
            AgentManifest agent = repository.loadAgent(artifactName);
            if (agent != null) return Optional.of(converter.convertAgent(agent));
        } catch (Exception ignored) {}

        try {
            PipelineManifest pipeline = repository.loadPipeline(artifactName);
            if (pipeline != null) return Optional.of(converter.convertPipeline(pipeline));
        } catch (Exception ignored) {}

        try {
            SkillMetadata skill = repository.getSkillMetadata(artifactName);
            if (skill != null) return Optional.of(converter.convertSkill(skill));
        } catch (Exception ignored) {}

        return Optional.empty();
    }
}
