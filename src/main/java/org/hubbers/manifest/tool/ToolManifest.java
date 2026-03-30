package org.hubbers.manifest.tool;

import org.hubbers.manifest.agent.InputDefinition;
import org.hubbers.manifest.agent.OutputDefinition;
import org.hubbers.manifest.common.ExampleDefinition;
import org.hubbers.manifest.common.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToolManifest {
    private Metadata tool;
    private String type;
    private Map<String, Object> config;
    private InputDefinition input;
    private OutputDefinition output;
    private List<ExampleDefinition> examples = new ArrayList<>();

    public Metadata getTool() { return tool; }
    public void setTool(Metadata tool) { this.tool = tool; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public InputDefinition getInput() { return input; }
    public void setInput(InputDefinition input) { this.input = input; }
    public OutputDefinition getOutput() { return output; }
    public void setOutput(OutputDefinition output) { this.output = output; }
    public List<ExampleDefinition> getExamples() { return examples; }
    public void setExamples(List<ExampleDefinition> examples) { this.examples = examples; }
}
