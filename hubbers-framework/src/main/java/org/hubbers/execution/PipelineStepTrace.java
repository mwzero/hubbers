package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a single step in a pipeline execution.
 */
public class PipelineStepTrace {
    private int stepNumber;
    private String stepName;
    private String artifactType; // "agent", "tool", "pipeline", "skill"
    private String artifactName;
    private ExecutionStatus status;
    private JsonNode input;
    private JsonNode output;
    private long startTime;
    private long endTime;
    private long durationMs;
    private String error;

    public PipelineStepTrace() {}

    public PipelineStepTrace(int stepNumber, String stepName, String artifactType, String artifactName) {
        this.stepNumber = stepNumber;
        this.stepName = stepName;
        this.artifactType = artifactType;
        this.artifactName = artifactName;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    
    public String getArtifactType() { return artifactType; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
    
    public String getArtifactName() { return artifactName; }
    public void setArtifactName(String artifactName) { this.artifactName = artifactName; }
    
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    
    public JsonNode getInput() { return input; }
    public void setInput(JsonNode input) { this.input = input; }
    
    public JsonNode getOutput() { return output; }
    public void setOutput(JsonNode output) { this.output = output; }
    
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
