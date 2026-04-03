package org.hubbers.manifest.agent;

import lombok.Data;
import org.hubbers.forms.FormTrigger;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentManifest {
    private Metadata agent;
    private ModelConfig model;
    private Instructions instructions;
    private InputDefinition input;
    private OutputDefinition output;
    private Map<String, Object> config = new HashMap<>();
    private List<ToolReference> tools = new ArrayList<>();
    private List<ExampleDefinition> examples = new ArrayList<>();
    private FormTrigger forms;
}
