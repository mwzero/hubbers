package org.hubbers.model;

import lombok.Data;

import java.util.List;

@Data
public class ModelRequest {
    private String systemPrompt;
    private String userPrompt;
    private String model;
    private Double temperature;

    /** Top-level {@code think} field for Ollama thinking models (e.g. qwen3). */
    private Boolean think;

    // For function calling - functions available to the LLM
    private List<FunctionDefinition> functions;
    
    // For multi-turn conversations (alternative to systemPrompt/userPrompt)
    private List<Message> messages;
}
