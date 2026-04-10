package org.hubbers.model;

import java.util.List;

public class ModelRequest {
    private String systemPrompt;
    private String userPrompt;
    private String model;
    private Double temperature;
    
    // For function calling - functions available to the LLM
    private List<FunctionDefinition> functions;
    
    // For multi-turn conversations (alternative to systemPrompt/userPrompt)
    private List<Message> messages;

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    
    public List<FunctionDefinition> getFunctions() { return functions; }
    public void setFunctions(List<FunctionDefinition> functions) { this.functions = functions; }
    
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
}
