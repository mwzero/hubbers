package org.hubbers.execution;

public class ExecutionMetadata {
    private long startedAt;
    private long endedAt;
    private String details;

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
