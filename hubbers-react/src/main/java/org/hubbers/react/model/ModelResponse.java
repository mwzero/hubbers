package org.hubbers.react.model;

import lombok.Data;

import java.util.List;

/**
 * Response received from a {@link ModelProvider}.
 */
@Data
public class ModelResponse {
    private String content;
    private String model;
    private long latencyMs;

    /** Functions that the LLM wants to call (function calling). */
    private List<FunctionCall> functionCalls;
    private String finishReason; // "stop", "function_call", "length", "content_filter"

    // Token usage tracking
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}
