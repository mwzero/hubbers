package org.hubbers.model;

import java.util.List;

public class ModelResponse {
    private String content;
    private String model;
    private long latencyMs;
    
    // For function calling - functions that the LLM wants to call
    private List<FunctionCall> functionCalls;
    private String finishReason;  // "stop", "function_call", "length", "content_filter"

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    
    public List<FunctionCall> getFunctionCalls() { return functionCalls; }
    public void setFunctionCalls(List<FunctionCall> functionCalls) { this.functionCalls = functionCalls; }
    
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
}
