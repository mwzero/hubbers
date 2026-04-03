package org.hubbers.manifest.pipeline;

import lombok.Data;
import org.hubbers.forms.FormTrigger;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.List;

@Data
public class PipelineManifest {
    private Metadata pipeline;
    private List<PipelineStep> steps = new ArrayList<>();
    private List<ExampleDefinition> examples = new ArrayList<>();
    private FormTrigger forms;
}
