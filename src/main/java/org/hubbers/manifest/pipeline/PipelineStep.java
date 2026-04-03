package org.hubbers.manifest.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hubbers.forms.FormDefinition;

import java.util.Map;

@Data
public class PipelineStep {
    private String id;
    private String agent;
    private String tool;
    @JsonProperty("input_mapping")
    private Map<String, String> inputMapping;
    private FormDefinition form; // Form to show during this step (human-in-the-loop)
}
