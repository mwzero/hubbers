package org.hubbers.model;

import lombok.Data;

import java.util.List;

@Data
public class ModelRequest {
    private String systemPrompt;
    private String userPrompt;
    private String model;
    private Double temperature;
    
    // For function calling - functions available to the LLM
    private List<FunctionDefinition> functions;
    
    // For multi-turn conversations (alternative to systemPrompt/userPrompt)
    private List<Message> messages;
}
