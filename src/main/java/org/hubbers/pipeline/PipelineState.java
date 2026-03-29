package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public class PipelineState {
    private final Map<String, JsonNode> stepOutputs = new HashMap<>();

    public void putStepOutput(String stepId, JsonNode output) {
        stepOutputs.put(stepId, output);
    }

    public JsonNode getStepOutput(String stepId) {
        return stepOutputs.get(stepId);
    }
}
