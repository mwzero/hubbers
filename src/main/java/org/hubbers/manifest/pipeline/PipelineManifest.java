package org.hubbers.manifest.pipeline;

import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.List;

public class PipelineManifest {
    private Metadata pipeline;
    private List<PipelineStep> steps = new ArrayList<>();
    private List<ExampleDefinition> examples = new ArrayList<>();

    public Metadata getPipeline() { return pipeline; }
    public void setPipeline(Metadata pipeline) { this.pipeline = pipeline; }
    public List<PipelineStep> getSteps() { return steps; }
    public void setSteps(List<PipelineStep> steps) { this.steps = steps; }
    public List<ExampleDefinition> getExamples() { return examples; }
    public void setExamples(List<ExampleDefinition> examples) { this.examples = examples; }
}
