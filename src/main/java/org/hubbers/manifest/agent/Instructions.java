package org.hubbers.manifest.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Instructions {
    @JsonProperty("system_prompt")
    private String systemPrompt;

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}
