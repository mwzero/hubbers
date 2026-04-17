package org.hubbers.execution;

/**
 * Represents a skill invocation within an agent or pipeline execution.
 */
public class SkillInvocationTrace {
    private String skillName;
    private String executionMode; // "llm-prompt", "script", "hybrid"
    private long durationMs;
    private boolean success;
    private String error;

    public SkillInvocationTrace() {}

    public SkillInvocationTrace(String skillName, String executionMode, long durationMs, boolean success) {
        this.skillName = skillName;
        this.executionMode = executionMode;
        this.durationMs = durationMs;
        this.success = success;
    }

    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
