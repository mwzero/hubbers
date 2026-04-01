package org.hubbers.manifest.agent;

import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentManifest {
    private Metadata agent;
    private ModelConfig model;
    private Instructions instructions;
    private InputDefinition input;
    private OutputDefinition output;
    private Map<String, Object> config = new HashMap<>();
    private List<ToolReference> tools = new ArrayList<>();
    private List<ExampleDefinition> examples = new ArrayList<>();

    public Metadata getAgent() { return agent; }
    public void setAgent(Metadata agent) { this.agent = agent; }
    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }
    public Instructions getInstructions() { return instructions; }
    public void setInstructions(Instructions instructions) { this.instructions = instructions; }
    public InputDefinition getInput() { return input; }
    public void setInput(InputDefinition input) { this.input = input; }
    public OutputDefinition getOutput() { return output; }
    public void setOutput(OutputDefinition output) { this.output = output; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public List<ToolReference> getTools() { return tools; }
    public void setTools(List<ToolReference> tools) { this.tools = tools; }
    public List<ExampleDefinition> getExamples() { return examples; }
    public void setExamples(List<ExampleDefinition> examples) { this.examples = examples; }
}
