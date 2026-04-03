package org.hubbers.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages filesystem operations for execution logs and artifacts.
 * Handles directory creation, file storage, and retrieval of execution data.
 */
public class ExecutionStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionStorageService.class);
    
    private final Path executionsRoot;
    private final String repoPath;
    
    /**
     * Creates a new ExecutionStorageService.
     * 
     * @param executionsPath path to store executions (absolute or relative to repo)
     * @param repoPath repository root path
     */
    public ExecutionStorageService(String executionsPath, String repoPath) {
        this.repoPath = repoPath;
        this.executionsRoot = resolveExecutionsPath(executionsPath, repoPath);
        ensureDirectoryExists(executionsRoot);
        logger.info("Execution storage initialized at: {}", executionsRoot.toAbsolutePath());
    }
    
    /**
     * Resolves the executions path, handling absolute paths, relative paths,
     * and environment variables like ${HOME}.
     */
    private Path resolveExecutionsPath(String executionsPath, String repoPath) {
        // Handle environment variables
        String resolved = executionsPath;
        if (resolved.contains("${")) {
            int start = resolved.indexOf("${");
            int end = resolved.indexOf("}", start);
            if (end > start) {
                String envVar = resolved.substring(start + 2, end);
                String envValue = System.getenv(envVar);
                if (envValue == null) {
                    envValue = System.getProperty(envVar);
                }
                if (envValue != null) {
                    resolved = resolved.replace("${" + envVar + "}", envValue);
                }
            }
        }
        
        Path path = Paths.get(resolved);
        
        // If not absolute, resolve relative to repo
        if (!path.isAbsolute()) {
            path = Paths.get(repoPath).resolve(path);
        }
        
        return path.normalize();
    }
    
    /**
     * Creates a new execution directory.
     * 
     * @param executionId the execution ID
     * @return the path to the execution directory
     * @throws IOException if directory creation fails
     */
    public Path createExecutionDirectory(String executionId) throws IOException {
        Path executionDir = executionsRoot.resolve(executionId);
        Files.createDirectories(executionDir);
        logger.debug("Created execution directory: {}", executionDir);
        return executionDir;
    }
    
    /**
     * Creates a steps subdirectory for pipeline executions.
     * 
     * @param executionId the execution ID
     * @return the path to the steps directory
     * @throws IOException if directory creation fails
     */
    public Path createStepsDirectory(String executionId) throws IOException {
        Path stepsDir = getExecutionPath(executionId).resolve("steps");
        Files.createDirectories(stepsDir);
        return stepsDir;
    }
    
    /**
     * Creates a directory for a specific pipeline step.
     * 
     * @param executionId the execution ID
     * @param stepIndex the step index (0-based)
     * @param stepName the step name
     * @return the path to the step directory
     * @throws IOException if directory creation fails
     */
    public Path createStepDirectory(String executionId, int stepIndex, String stepName) throws IOException {
        Path stepsDir = createStepsDirectory(executionId);
        String stepDirName = String.format("%02d-%s", stepIndex, sanitizeFilename(stepName));
        Path stepDir = stepsDir.resolve(stepDirName);
        Files.createDirectories(stepDir);
        return stepDir;
    }
    
    /**
     * Gets the path to an execution directory.
     * 
     * @param executionId the execution ID
     * @return the path to the execution directory
     */
    public Path getExecutionPath(String executionId) {
        return executionsRoot.resolve(executionId);
    }
    
    /**
     * Checks if an execution directory exists.
     * 
     * @param executionId the execution ID
     * @return true if exists, false otherwise
     */
    public boolean executionExists(String executionId) {
        return Files.exists(getExecutionPath(executionId));
    }
    
    /**
     * Writes content to a file in the execution directory.
     * 
     * @param executionId the execution ID
     * @param filename the filename (e.g., "input.json", "output.json")
     * @param content the content to write
     * @throws IOException if write fails
     */
    public void writeFile(String executionId, String filename, String content) throws IOException {
        Path filePath = getExecutionPath(executionId).resolve(filename);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    /**
     * Appends content to a file in the execution directory.
     * 
     * @param executionId the execution ID
     * @param filename the filename
     * @param content the content to append
     * @throws IOException if write fails
     */
    public void appendToFile(String executionId, String filename, String content) throws IOException {
        Path filePath = getExecutionPath(executionId).resolve(filename);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    
    /**
     * Reads content from a file in the execution directory.
     * 
     * @param executionId the execution ID
     * @param filename the filename
     * @return the file content
     * @throws IOException if read fails
     */
    public String readFile(String executionId, String filename) throws IOException {
        Path filePath = getExecutionPath(executionId).resolve(filename);
        return Files.readString(filePath);
    }
    
    /**
     * Lists all execution IDs, sorted by creation time (newest first).
     * 
     * @return list of execution IDs
     * @throws IOException if listing fails
     */
    public List<String> listExecutions() throws IOException {
        if (!Files.exists(executionsRoot)) {
            return List.of();
        }
        
        try (Stream<Path> stream = Files.list(executionsRoot)) {
            return stream
                .filter(Files::isDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(ExecutionIdGenerator::isValid)
                .sorted((a, b) -> b.compareTo(a)) // Sort descending (newest first)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Gets the root path where executions are stored.
     * 
     * @return the executions root path
     */
    public Path getExecutionsRoot() {
        return executionsRoot;
    }
    
    /**
     * Ensures a directory exists, creating it if necessary.
     */
    private void ensureDirectoryExists(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create executions directory: " + path, e);
        }
    }
    
    /**
     * Sanitizes a filename by replacing invalid characters.
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
