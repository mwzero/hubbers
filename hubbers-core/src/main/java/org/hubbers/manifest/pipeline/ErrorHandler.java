package org.hubbers.manifest.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error handling configuration for a pipeline step.
 *
 * <p>Determines what happens when a step fails after all retry attempts
 * are exhausted. The {@code action} field controls the behavior:</p>
 * <ul>
 *   <li>{@code FAIL} — stop the pipeline immediately (default)</li>
 *   <li>{@code SKIP} — skip the failed step and continue to the next one</li>
 *   <li>{@code FALLBACK} — execute an alternative step identified by {@code fallbackStep}</li>
 * </ul>
 *
 * <p>Example YAML usage:
 * <pre>{@code
 * steps:
 *   - id: risky_step
 *     tool: http.request
 *     on_error:
 *       action: skip
 *   - id: critical_step
 *     tool: sql.query
 *     on_error:
 *       action: fallback
 *       fallback_step: safe_default
 * }</pre>
 *
 * @see PipelineStep
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorHandler {

    /**
     * The action to take when a step fails.
     */
    public enum Action {
        /** Stop the pipeline immediately with an error. */
        FAIL,
        /** Skip the failed step and continue to the next one. */
        SKIP,
        /** Execute the fallback step instead. */
        FALLBACK
    }

    /** The error handling action. Defaults to {@code FAIL}. */
    @Builder.Default
    private Action action = Action.FAIL;

    /** The step ID to execute as fallback when action is {@code FALLBACK}. */
    @JsonProperty("fallback_step")
    private String fallbackStep;
}
