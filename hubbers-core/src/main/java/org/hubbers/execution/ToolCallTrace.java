package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a single tool invocation within an agent's execution.
 */
public class ToolCallTrace {
    private String toolName;
    private JsonNode input;
    private JsonNode output;
    private long durationMs;
    private boolean success;
    private String error;

    public ToolCallTrace() {}

    public ToolCallTrace(String toolName, JsonNode input, JsonNode output, long durationMs, boolean success) {
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.durationMs = durationMs;
        this.success = success;
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    
    public JsonNode getInput() { return input; }
    public void setInput(JsonNode input) { this.input = input; }
    
    public JsonNode getOutput() { return output; }
    public void setOutput(JsonNode output) { this.output = output; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
