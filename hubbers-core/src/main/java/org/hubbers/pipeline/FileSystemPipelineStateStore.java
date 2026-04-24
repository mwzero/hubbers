package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * File-system-backed implementation of {@link PipelineStateStore}.
 *
 * <p>Persists pipeline execution snapshots as JSON files under
 * {@code {baseDir}/_executions/{executionId}/pipeline-state.json}.</p>
 *
 * @see PipelineStateStore
 * @see PipelineExecutionSnapshot
 */
@Slf4j
public class FileSystemPipelineStateStore implements PipelineStateStore {

    private static final String STATE_FILE = "pipeline-state.json";

    private final Path baseDir;
    private final ObjectMapper mapper;

    /**
     * Creates a new store rooted at the given base directory.
     *
     * @param baseDir the root directory for pipeline state files
     * @param mapper  the Jackson ObjectMapper for JSON serialization
     */
    public FileSystemPipelineStateStore(Path baseDir, ObjectMapper mapper) {
        this.baseDir = baseDir;
        this.mapper = mapper;
    }

    @Override
    public void save(String executionId, PipelineExecutionSnapshot snapshot) {
        Path dir = resolveDir(executionId);
        try {
            Files.createDirectories(dir);
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(STATE_FILE).toFile(), snapshot);
            log.debug("Saved pipeline state for execution '{}'", executionId);
        } catch (IOException e) {
            log.error("Failed to save pipeline state for execution '{}'", executionId, e);
            throw new IllegalStateException("Cannot save pipeline state: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PipelineExecutionSnapshot> load(String executionId) {
        Path file = resolveDir(executionId).resolve(STATE_FILE);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            PipelineExecutionSnapshot snapshot = mapper.readValue(file.toFile(), PipelineExecutionSnapshot.class);
            log.debug("Loaded pipeline state for execution '{}'", executionId);
            return Optional.of(snapshot);
        } catch (IOException e) {
            log.error("Failed to load pipeline state for execution '{}'", executionId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String executionId) {
        Path dir = resolveDir(executionId);
        Path file = dir.resolve(STATE_FILE);
        try {
            Files.deleteIfExists(file);
            // Delete directory if empty
            if (Files.exists(dir) && Files.list(dir).findAny().isEmpty()) {
                Files.delete(dir);
            }
            log.debug("Deleted pipeline state for execution '{}'", executionId);
        } catch (IOException e) {
            log.warn("Failed to delete pipeline state for execution '{}'", executionId, e);
        }
    }

    private Path resolveDir(String executionId) {
        // Sanitize executionId to prevent path traversal
        String sanitized = executionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return baseDir.resolve(sanitized);
    }
}
