package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single iteration in an agent's ReAct loop.
 */
public class AgentIterationTrace {
    private int iterationNumber;
    private String reasoning;
    private List<ToolCallTrace> toolCalls = new ArrayList<>();
    private JsonNode result;
    private long durationMs;
    private boolean isComplete;

    public AgentIterationTrace() {}

    public AgentIterationTrace(int iterationNumber) {
        this.iterationNumber = iterationNumber;
    }

    public int getIterationNumber() { return iterationNumber; }
    public void setIterationNumber(int iterationNumber) { this.iterationNumber = iterationNumber; }
    
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    
    public List<ToolCallTrace> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallTrace> toolCalls) { this.toolCalls = toolCalls; }
    
    public void addToolCall(ToolCallTrace toolCall) {
        this.toolCalls.add(toolCall);
    }
    
    public JsonNode getResult() { return result; }
    public void setResult(JsonNode result) { this.result = result; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public boolean isComplete() { return isComplete; }
    public void setComplete(boolean complete) { isComplete = complete; }
}
