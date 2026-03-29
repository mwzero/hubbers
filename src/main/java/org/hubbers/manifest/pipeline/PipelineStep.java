package org.hubbers.manifest.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class PipelineStep {
    private String id;
    private String agent;
    private String tool;
    @JsonProperty("input_mapping")
    private Map<String, String> inputMapping;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public Map<String, String> getInputMapping() { return inputMapping; }
    public void setInputMapping(Map<String, String> inputMapping) { this.inputMapping = inputMapping; }
}
