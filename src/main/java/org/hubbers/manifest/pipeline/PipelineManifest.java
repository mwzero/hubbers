package org.hubbers.manifest.pipeline;

import org.hubbers.manifest.common.Metadata;

import java.util.ArrayList;
import java.util.List;

public class PipelineManifest {
    private Metadata pipeline;
    private List<PipelineStep> steps = new ArrayList<>();

    public Metadata getPipeline() { return pipeline; }
    public void setPipeline(Metadata pipeline) { this.pipeline = pipeline; }
    public List<PipelineStep> getSteps() { return steps; }
    public void setSteps(List<PipelineStep> steps) { this.steps = steps; }
}
