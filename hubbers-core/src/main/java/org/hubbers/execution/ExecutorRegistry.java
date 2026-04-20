package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.agent.AgenticExecutor;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.skill.SkillExecutor;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Central registry for artifact executors.
 * 
 * <p>This class acts as a mediator to break circular dependencies between
 * AgenticExecutor and PipelineExecutor. Instead of executors holding direct
 * references to each other, they can look up executors through this registry.</p>
 * 
 * <p>Pattern: Mediator + Registry</p>
 * 
 * @since 0.1.0
 */
public class ExecutorRegistry {
    private static final Logger log = LoggerFactory.getLogger(ExecutorRegistry.class);
    
    private final Map<ExecutorType, Object> executors = new ConcurrentHashMap<>();
    
    /**
     * Types of executors that can be registered.
     */
    public enum ExecutorType {
        AGENT,      // AgenticExecutor
        TOOL,       // ToolExecutor
        PIPELINE,   // PipelineExecutor
        SKILL       // SkillExecutor
    }
    
    /**
     * Register an executor.
     * 
     * @param type the executor type
     * @param executor the executor instance
     */
    public void register(ExecutorType type, Object executor) {
        log.debug("Registering executor of type: {}", type);
        executors.put(type, executor);
    }
    
    /**
     * Get the AgenticExecutor.
     * 
     * @return the AgenticExecutor instance
     * @throws IllegalStateException if not registered
     */
    public AgenticExecutor getAgenticExecutor() {
        AgenticExecutor executor = (AgenticExecutor) executors.get(ExecutorType.AGENT);
        if (executor == null) {
            throw new IllegalStateException("AgenticExecutor not registered");
        }
        return executor;
    }
    
    /**
     * Get the ToolExecutor.
     * 
     * @return the ToolExecutor instance
     * @throws IllegalStateException if not registered
     */
    public ToolExecutor getToolExecutor() {
        ToolExecutor executor = (ToolExecutor) executors.get(ExecutorType.TOOL);
        if (executor == null) {
            throw new IllegalStateException("ToolExecutor not registered");
        }
        return executor;
    }
    
    /**
     * Get the PipelineExecutor.
     * 
     * @return the PipelineExecutor instance
     * @throws IllegalStateException if not registered
     */
    public PipelineExecutor getPipelineExecutor() {
        PipelineExecutor executor = (PipelineExecutor) executors.get(ExecutorType.PIPELINE);
        if (executor == null) {
            throw new IllegalStateException("PipelineExecutor not registered");
        }
        return executor;
    }
    
    /**
     * Get the SkillExecutor.
     * 
     * @return the SkillExecutor instance
     * @throws IllegalStateException if not registered
     */
    public SkillExecutor getSkillExecutor() {
        SkillExecutor executor = (SkillExecutor) executors.get(ExecutorType.SKILL);
        if (executor == null) {
            throw new IllegalStateException("SkillExecutor not registered");
        }
        return executor;
    }
    
    /**
     * Execute a tool by name.
     * 
     * @param toolManifest the tool manifest
     * @param input the input data
     * @return the execution result
     */
    public RunResult executeTool(ToolManifest toolManifest, JsonNode input) {
        return getToolExecutor().execute(toolManifest, input);
    }
    
    /**
     * Execute an agent by name with conversation support.
     * 
     * @param agentManifest the agent manifest
     * @param input the input data
     * @param conversationId optional conversation ID for multi-turn
     * @return the execution result
     */
    public RunResult executeAgent(AgentManifest agentManifest, JsonNode input, String conversationId) {
        return getAgenticExecutor().execute(agentManifest, input, conversationId);
    }
    
    /**
     * Execute a pipeline by name.
     * 
     * @param pipelineManifest the pipeline manifest
     * @param input the input data
     * @return the execution result
     */
    public RunResult executePipeline(PipelineManifest pipelineManifest, JsonNode input) {
        return getPipelineExecutor().execute(pipelineManifest, input);
    }
    
    /**
     * Execute a skill by name.
     * 
     * @param skillManifest the skill manifest
     * @param input the input data
     * @return the execution result
     */
    public RunResult executeSkill(SkillManifest skillManifest, JsonNode input) {
        return getSkillExecutor().execute(skillManifest, input);
    }
    
    /**
     * Check if an executor is registered.
     * 
     * @param type the executor type
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(ExecutorType type) {
        return executors.containsKey(type);
    }
}
