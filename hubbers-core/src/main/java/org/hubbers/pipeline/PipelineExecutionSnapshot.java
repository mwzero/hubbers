package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Serializable snapshot of a paused pipeline execution.
 *
 * <p>Contains all the state needed to resume a pipeline from the point it was paused,
 * including the original manifest name, input, completed step outputs, and the index
 * of the step that is waiting for input.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionSnapshot {

    /** The pipeline name (used to reload the manifest). */
    private String pipelineName;

    /** The original input that was passed to the pipeline. */
    private JsonNode originalInput;

    /** Outputs from already-completed steps, keyed by step ID. */
    private Map<String, JsonNode> completedStepOutputs;

    /** The zero-based index of the step that is paused (waiting for form input). */
    private int pausedAtStepIndex;

    /** The step ID that triggered the pause. */
    private String pausedAtStepId;

    /** Timestamp when the pipeline was paused. */
    private long pausedAt;
}
