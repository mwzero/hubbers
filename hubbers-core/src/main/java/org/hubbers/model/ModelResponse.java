package org.hubbers.model;

import lombok.Data;

import java.util.List;

@Data
public class ModelResponse {
    private String content;
    private String model;
    private long latencyMs;
    
    // For function calling - functions that the LLM wants to call
    private List<FunctionCall> functionCalls;
    private String finishReason;  // "stop", "function_call", "length", "content_filter"

    // Token usage tracking
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}
