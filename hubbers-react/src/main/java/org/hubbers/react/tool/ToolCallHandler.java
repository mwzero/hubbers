package org.hubbers.react.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.react.execution.RunResult;

/**
 * Abstraction for dispatching a function call (tool, agent, pipeline, or skill) by name.
 *
 * <p>Implementations in {@code hubbers-core} resolve the artifact by name from
 * the repository and delegate to the appropriate executor (ToolExecutor, HubberAgentLoop,
 * PipelineExecutor, SkillExecutor).</p>
 */
@FunctionalInterface
public interface ToolCallHandler {

    /**
     * Executes the named artifact with the given input arguments.
     *
     * @param artifactName the name of the tool, agent, pipeline, or skill to execute
     * @param arguments    the JSON arguments to pass to the artifact
     * @return the execution result
     */
    RunResult handle(String artifactName, JsonNode arguments);
}
