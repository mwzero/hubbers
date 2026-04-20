package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.manifest.agent.AgentManifest;

public class AgentRunContext {
    private AgentManifest manifest;
    private JsonNode input;

    public AgentManifest getManifest() { return manifest; }
    public void setManifest(AgentManifest manifest) { this.manifest = manifest; }
    public JsonNode getInput() { return input; }
    public void setInput(JsonNode input) { this.input = input; }
}
