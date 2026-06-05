package org.hubbers.react.execution;

/**
 * Represents a single skill invocation during agent or pipeline execution.
 */
public class SkillInvocationTrace {
    private String skillName;
    private String input;
    private String output;
    private long durationMs;
    private boolean success;
    private String error;

    public SkillInvocationTrace() {}

    public SkillInvocationTrace(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
