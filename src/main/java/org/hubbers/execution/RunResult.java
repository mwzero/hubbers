package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;

public class RunResult {
    private String executionId;
    private ExecutionStatus status;
    private JsonNode output;
    private String error;
    private ExecutionMetadata metadata;

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

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public JsonNode getOutput() { return output; }
    public void setOutput(JsonNode output) { this.output = output; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public ExecutionMetadata getMetadata() { return metadata; }
    public void setMetadata(ExecutionMetadata metadata) { this.metadata = metadata; }
}
