package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.react.execution.RunResult;
import org.hubbers.react.tool.ToolCallHandler;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform implementation of {@link ToolCallHandler}.
 *
 * <p>Dispatches function calls (tool, nested agent, pipeline, skill) by resolving
 * the artifact name from the repository and forwarding to the appropriate executor.</p>
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Tool → {@link ToolExecutor}</li>
 *   <li>Agent → {@link org.hubbers.react.HubberAgentLoop} (via {@link ExecutorRegistry})</li>
 *   <li>Pipeline → {@link org.hubbers.pipeline.PipelineExecutor} (via {@link ExecutorRegistry})</li>
 *   <li>Skill → {@link org.hubbers.skill.SkillExecutor} (via {@link ExecutorRegistry})</li>
 * </ol>
 * If the artifact is not found a failed {@link RunResult} is returned.</p>
 */
public class ArtifactDispatchHandler implements ToolCallHandler {

    private static final Logger log = LoggerFactory.getLogger(ArtifactDispatchHandler.class);

    private final ArtifactRepository repository;
    private final ToolExecutor toolExecutor;
    private final ExecutorRegistry executorRegistry;

    /**
     * Creates a new dispatch handler.
     *
     * @param repository      artifact repository
     * @param toolExecutor    executor for tool artifacts
     * @param executorRegistry registry giving access to agent, pipeline, and skill executors
     */
    public ArtifactDispatchHandler(ArtifactRepository repository,
                                   ToolExecutor toolExecutor,
                                   ExecutorRegistry executorRegistry) {
        this.repository = repository;
        this.toolExecutor = toolExecutor;
        this.executorRegistry = executorRegistry;
    }

    @Override
    public RunResult handle(String artifactName, JsonNode arguments) {
        // Tool
        try {
            ToolManifest tool = repository.loadTool(artifactName);
            if (tool != null) return toolExecutor.execute(tool, arguments);
        } catch (Exception ignored) {}

        // Nested agent (recursive — adapter converts manifest to config)
        try {
            AgentManifest agent = repository.loadAgent(artifactName);
            if (agent != null) {
                org.hubbers.react.AgentConfig agentConfig =
                        AgentConfigAdapter.from(agent);
                return executorRegistry.getAgentExecutor().execute(agentConfig, arguments != null ? arguments.toString() : "", null);
            }
        } catch (Exception e) {
            log.debug("Not an agent artifact '{}': {}", artifactName, e.getMessage());
        }

        // Pipeline
        try {
            PipelineManifest pipeline = repository.loadPipeline(artifactName);
            if (pipeline != null) {
                if (executorRegistry.isRegistered(ExecutorRegistry.ExecutorType.PIPELINE)) {
                    return executorRegistry.executePipeline(pipeline, arguments);
                }
                return RunResult.failed("Pipeline execution not available: PipelineExecutor not initialised");
            }
        } catch (Exception e) {
            return RunResult.failed("Artifact execution failed: " + e.getMessage());
        }

        // Skill
        try {
            SkillManifest skill = repository.loadSkill(artifactName);
            if (skill != null) {
                if (executorRegistry.isRegistered(ExecutorRegistry.ExecutorType.SKILL)) {
                    return executorRegistry.executeSkill(skill, arguments);
                }
                return RunResult.failed("Skill execution not available: SkillExecutor not initialised");
            }
        } catch (Exception ignored) {}

        return RunResult.failed("Artifact not found: " + artifactName);
    }
}
