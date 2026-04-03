package org.hubbers.manifest.tool;

import lombok.Data;
import org.hubbers.forms.FormTrigger;
import org.hubbers.manifest.agent.InputDefinition;
import org.hubbers.manifest.agent.OutputDefinition;
import org.hubbers.manifest.common.ExampleDefinition;
import org.hubbers.manifest.common.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ToolManifest {
    private Metadata tool;
    private String type;
    private Map<String, Object> config;
    private InputDefinition input;
    private OutputDefinition output;
    private List<ExampleDefinition> examples = new ArrayList<>();
    private FormTrigger forms;
}
