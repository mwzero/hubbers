package org.hubbers.pipeline;

import java.util.Optional;

/**
 * Persistence interface for pipeline execution state.
 *
 * <p>Used by the pipeline executor to persist state when a pipeline is paused
 * (e.g., waiting for human-in-the-loop form input) and to restore state when
 * the pipeline is resumed.</p>
 *
 * @see FileSystemPipelineStateStore
 * @see PipelineExecutor
 */
public interface PipelineStateStore {

    /**
     * Saves the pipeline execution state under the given execution ID.
     *
     * @param executionId unique identifier for this pipeline execution
     * @param snapshot    the execution snapshot to persist
     */
    void save(String executionId, PipelineExecutionSnapshot snapshot);

    /**
     * Loads a previously saved pipeline execution state.
     *
     * @param executionId the execution ID to look up
     * @return the snapshot if found, or empty if not found
     */
    Optional<PipelineExecutionSnapshot> load(String executionId);

    /**
     * Deletes the persisted state for a completed or cancelled execution.
     *
     * @param executionId the execution ID to remove
     */
    void delete(String executionId);
}
