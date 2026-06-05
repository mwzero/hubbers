package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgentConfigAdapter;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.react.AgentConfig;
import org.hubbers.react.execution.RunResult;
import org.hubbers.skill.SkillExecutor;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for artifact executors.
 *
 * <p>Acts as a mediator to break circular dependencies between
 * AgentExecutor and PipelineExecutor.</p>
 *
 * @since 0.1.0
 */
public class ExecutorRegistry {
    private static final Logger log = LoggerFactory.getLogger(ExecutorRegistry.class);

    private final Map<ExecutorType, Object> executors = new ConcurrentHashMap<>();

    /** Types of executors that can be registered. */
    public enum ExecutorType {
        AGENT,
        TOOL,
        PIPELINE,
        SKILL
    }

    public void register(ExecutorType type, Object executor) {
        log.debug("Registering executor of type: {}", type);
        executors.put(type, executor);
    }

    /**
     * Get the {@link org.hubbers.react.HubberAgentLoop} instance.
     *
     * @return the HubberAgentLoop instance
     * @throws IllegalStateException if not registered
     */
    public org.hubbers.react.HubberAgentLoop getAgentExecutor() {
        org.hubbers.react.HubberAgentLoop executor =
                (org.hubbers.react.HubberAgentLoop) executors.get(ExecutorType.AGENT);
        if (executor == null) {
            throw new IllegalStateException("HubberAgentLoop not registered");
        }
        return executor;
    }

    public ToolExecutor getToolExecutor() {
        ToolExecutor executor = (ToolExecutor) executors.get(ExecutorType.TOOL);
        if (executor == null) throw new IllegalStateException("ToolExecutor not registered");
        return executor;
    }

    public PipelineExecutor getPipelineExecutor() {
        PipelineExecutor executor = (PipelineExecutor) executors.get(ExecutorType.PIPELINE);
        if (executor == null) throw new IllegalStateException("PipelineExecutor not registered");
        return executor;
    }

    public SkillExecutor getSkillExecutor() {
        SkillExecutor executor = (SkillExecutor) executors.get(ExecutorType.SKILL);
        if (executor == null) throw new IllegalStateException("SkillExecutor not registered");
        return executor;
    }

    public RunResult executeTool(ToolManifest toolManifest, JsonNode input) {
        return getToolExecutor().execute(toolManifest, input);
    }

    public RunResult executeAgent(AgentManifest agentManifest, JsonNode input, String conversationId) {
        AgentConfig config = AgentConfigAdapter.from(agentManifest);
        return getAgentExecutor().execute(config, input.toString(), conversationId);
    }

    public RunResult executePipeline(PipelineManifest pipelineManifest, JsonNode input) {
        return getPipelineExecutor().execute(pipelineManifest, input);
    }

    public RunResult executeSkill(SkillManifest skillManifest, JsonNode input) {
        return getSkillExecutor().execute(skillManifest, input);
    }

    public boolean isRegistered(ExecutorType type) {
        return executors.containsKey(type);
    }
}