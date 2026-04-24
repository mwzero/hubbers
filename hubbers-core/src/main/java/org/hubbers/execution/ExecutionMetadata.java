package org.hubbers.execution;

/**
 * Metadata captured during artifact execution, including timing and token usage.
 */
public class ExecutionMetadata {
    private long startedAt;
    private long endedAt;
    private String details;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

    /**
     * Add token counts from a model response to the running total.
     *
     * @param prompt prompt tokens used
     * @param completion completion tokens used
     */
    public void addTokenUsage(long prompt, long completion) {
        this.promptTokens += prompt;
        this.completionTokens += completion;
        this.totalTokens += (prompt + completion);
    }
}
