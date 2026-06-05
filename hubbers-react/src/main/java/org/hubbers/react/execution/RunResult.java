package org.hubbers.react.execution;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Result of executing any artifact (agent, tool, pipeline, skill).
 */
@Data
public class RunResult {
    private String executionId;
    private ExecutionStatus status;
    private JsonNode output;
    private String error;
    private ExecutionMetadata metadata;
    private ExecutionTrace executionTrace;

    /**
     * Creates a successful result with the given output.
     *
     * @param output the output JSON node
     * @return successful RunResult
     */
    public static RunResult success(JsonNode output) {
        RunResult result = new RunResult();
        result.setStatus(ExecutionStatus.SUCCESS);
        result.setOutput(output);
        return result;
    }

    /**
     * Creates a failed result with the given error message.
     *
     * @param error human-readable error description
     * @return failed RunResult
     */
    public static RunResult failed(String error) {
        RunResult result = new RunResult();
        result.setStatus(ExecutionStatus.FAILED);
        result.setError(error);
        return result;
    }

    /**
     * Creates a PAUSED result indicating the execution is waiting for external input.
     *
     * @param executionId the execution ID to use when resuming
     * @return a RunResult with PAUSED status
     */
    public static RunResult pending(String executionId) {
        RunResult result = new RunResult();
        result.setStatus(ExecutionStatus.PAUSED);
        result.setExecutionId(executionId);
        return result;
    }
}
