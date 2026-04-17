package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class RunResult {
    private String executionId;
    private ExecutionStatus status;
    private JsonNode output;
    private String error;
    private ExecutionMetadata metadata;
    private ExecutionTrace executionTrace;

    public static RunResult success(JsonNode output) {
        RunResult result = new RunResult();
        result.setStatus(ExecutionStatus.SUCCESS);
        result.setOutput(output);
        return result;
    }

    public static RunResult failed(String error) {
        RunResult result = new RunResult();
        result.setStatus(ExecutionStatus.FAILED);
        result.setError(error);
        return result;
    }
}
