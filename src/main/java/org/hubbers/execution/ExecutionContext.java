package org.hubbers.execution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Tracks the execution context and state for an artifact execution.
 * Contains metadata about the execution including timing, status, and results.
 */
public class ExecutionContext {
    
    private String executionId;
    private String artifactType; // "agent", "tool", or "pipeline"
    private String artifactName;
    private ExecutionStatus status;
    private long startedAt;
    private Long endedAt;
    private JsonNode input;
    private JsonNode output;
    private String error;
    private String details; // Additional metadata (e.g., model name, latency)
    
    @JsonIgnore
    private transient ExecutionLogger logger;
    
    public ExecutionContext(String executionId, String artifactType, String artifactName) {
        this.executionId = executionId;
        this.artifactType = artifactType;
        this.artifactName = artifactName;
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = Instant.now().toEpochMilli();
    }
    
    /**
     * Marks the execution as completed successfully.
     */
    public void markSuccess(JsonNode output) {
        this.status = ExecutionStatus.SUCCESS;
        this.output = output;
        this.endedAt = Instant.now().toEpochMilli();
    }
    
    /**
     * Marks the execution as failed.
     */
    public void markFailed(String error) {
        this.status = ExecutionStatus.FAILED;
        this.error = error;
        this.endedAt = Instant.now().toEpochMilli();
    }
    
    /**
     * Marks the execution as paused (for human-in-the-loop).
     */
    public void markPaused() {
        this.status = ExecutionStatus.PAUSED;
    }
    
    /**
     * Resumes a paused execution.
     */
    public void resume() {
        this.status = ExecutionStatus.RUNNING;
    }
    
    /**
     * Gets the duration in milliseconds.
     */
    public long getDurationMs() {
        if (endedAt != null) {
            return endedAt - startedAt;
        }
        return Instant.now().toEpochMilli() - startedAt;
    }
    
    // Getters and setters
    
    public String getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }
    
    public String getArtifactType() {
        return artifactType;
    }
    
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }
    
    public String getArtifactName() {
        return artifactName;
    }
    
    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }
    
    public ExecutionStatus getStatus() {
        return status;
    }
    
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }
    
    public long getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
    
    public Long getEndedAt() {
        return endedAt;
    }
    
    public void setEndedAt(Long endedAt) {
        this.endedAt = endedAt;
    }
    
    public JsonNode getInput() {
        return input;
    }
    
    public void setInput(JsonNode input) {
        this.input = input;
    }
    
    public JsonNode getOutput() {
        return output;
    }
    
    public void setOutput(JsonNode output) {
        this.output = output;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public ExecutionLogger getLogger() {
        return logger;
    }
    
    public void setLogger(ExecutionLogger logger) {
        this.logger = logger;
    }
}
