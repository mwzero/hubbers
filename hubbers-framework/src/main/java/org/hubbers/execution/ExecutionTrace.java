package org.hubbers.execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Root execution trace containing all execution details for pipelines, agents, and skills.
 * Provides a hierarchical view of execution flow for post-execution analysis.
 */
public class ExecutionTrace {
    private String executionType; // "agent", "pipeline", "tool", "skill"
    private List<PipelineStepTrace> pipelineSteps = new ArrayList<>();
    private List<AgentIterationTrace> iterations = new ArrayList<>();
    private List<SkillInvocationTrace> skillInvocations = new ArrayList<>();
    private int totalIterations;
    private int totalSteps;

    public ExecutionTrace() {}

    public ExecutionTrace(String executionType) {
        this.executionType = executionType;
    }

    public String getExecutionType() { return executionType; }
    public void setExecutionType(String executionType) { this.executionType = executionType; }
    
    public List<PipelineStepTrace> getPipelineSteps() { return pipelineSteps; }
    public void setPipelineSteps(List<PipelineStepTrace> pipelineSteps) { this.pipelineSteps = pipelineSteps; }
    
    public void addPipelineStep(PipelineStepTrace step) {
        this.pipelineSteps.add(step);
        this.totalSteps = this.pipelineSteps.size();
    }
    
    public List<AgentIterationTrace> getIterations() { return iterations; }
    public void setIterations(List<AgentIterationTrace> iterations) { this.iterations = iterations; }
    
    public void addIteration(AgentIterationTrace iteration) {
        this.iterations.add(iteration);
        this.totalIterations = this.iterations.size();
    }
    
    public List<SkillInvocationTrace> getSkillInvocations() { return skillInvocations; }
    public void setSkillInvocations(List<SkillInvocationTrace> skillInvocations) { this.skillInvocations = skillInvocations; }
    
    public void addSkillInvocation(SkillInvocationTrace skill) {
        this.skillInvocations.add(skill);
    }
    
    public int getTotalIterations() { return totalIterations; }
    public void setTotalIterations(int totalIterations) { this.totalIterations = totalIterations; }
    
    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
}
