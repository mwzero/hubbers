package org.hubbers.manifest.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retry configuration for a pipeline step.
 *
 * <p>When a step fails, the pipeline executor will retry it up to
 * {@code maxRetries} times, waiting {@code backoffMs} milliseconds
 * between each attempt.</p>
 *
 * <p>Example YAML usage:
 * <pre>{@code
 * steps:
 *   - id: fetch_data
 *     tool: http.request
 *     retry:
 *       max_retries: 3
 *       backoff_ms: 1000
 * }</pre>
 *
 * @see PipelineStep
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryConfig {

    /** Maximum number of retry attempts (0 means no retries). */
    @JsonProperty("max_retries")
    @Builder.Default
    private int maxRetries = 0;

    /** Delay in milliseconds between retry attempts. */
    @JsonProperty("backoff_ms")
    @Builder.Default
    private long backoffMs = 1000L;
}
