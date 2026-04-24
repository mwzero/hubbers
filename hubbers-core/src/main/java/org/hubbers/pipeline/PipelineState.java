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

    /**
     * Returns a copy of all step outputs (for serialization when pausing).
     *
     * @return unmodifiable map of step ID to output
     */
    public Map<String, JsonNode> getAllStepOutputs() {
        return Map.copyOf(stepOutputs);
    }

    /**
     * Restores step outputs from a previously saved map (for resume after pause).
     *
     * @param outputs the outputs to restore
     */
    public void restoreOutputs(Map<String, JsonNode> outputs) {
        if (outputs != null) {
            stepOutputs.putAll(outputs);
        }
    }
}
