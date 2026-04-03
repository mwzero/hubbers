package org.hubbers.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.util.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handles logging for execution contexts.
 * Writes structured logs to execution directories.
 */
public class ExecutionLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionLogger.class);
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    
    private final ExecutionStorageService storageService;
    private final String executionId;
    private final ObjectMapper jsonMapper = JacksonFactory.jsonMapper();
    
    public ExecutionLogger(ExecutionStorageService storageService, String executionId) {
        this.storageService = storageService;
        this.executionId = executionId;
    }
    
    /**
     * Logs an info message to the execution log file.
     */
    public void info(String message) {
        log("INFO", message);
    }
    
    /**
     * Logs a warning message to the execution log file.
     */
    public void warn(String message) {
        log("WARN", message);
    }
    
    /**
     * Logs an error message to the execution log file.
     */
    public void error(String message) {
        log("ERROR", message);
    }
    
    /**
     * Logs an error message with exception details.
     */
    public void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(message).append("\n");
        sb.append("Exception: ").append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
        
        // Add stack trace
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        
        log("ERROR", sb.toString());
    }
    
    /**
     * Writes a log entry with timestamp and level.
     */
    private void log(String level, String message) {
        try {
            String timestamp = LOG_TIMESTAMP_FORMAT.format(Instant.now());
            String logEntry = String.format("[%s] %s - %s\n", timestamp, level, message);
            storageService.appendToFile(executionId, "execution-log.txt", logEntry);
        } catch (IOException e) {
            logger.error("Failed to write to execution log for {}: {}", executionId, e.getMessage());
        }
    }
    
    /**
     * Saves the execution metadata to metadata.json file.
     */
    public void saveMetadata(ExecutionContext context) {
        try {
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            storageService.writeFile(executionId, "execution-metadata.json", json);
        } catch (IOException e) {
            logger.error("Failed to save metadata for {}: {}", executionId, e.getMessage());
        }
    }
    
    /**
     * Saves the input to input.json file.
     */
    public void saveInput(Object input) {
        try {
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
            storageService.writeFile(executionId, "input.json", json);
        } catch (IOException e) {
            logger.error("Failed to save input for {}: {}", executionId, e.getMessage());
        }
    }
    
    /**
     * Saves the output to output.json file.
     */
    public void saveOutput(Object output) {
        try {
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            storageService.writeFile(executionId, "output.json", json);
        } catch (IOException e) {
            logger.error("Failed to save output for {}: {}", executionId, e.getMessage());
        }
    }
    
    /**
     * Creates a logger for a pipeline step.
     */
    public ExecutionLogger createStepLogger(int stepIndex, String stepName) {
        try {
            storageService.createStepDirectory(executionId, stepIndex, stepName);
            String stepDirName = String.format("%02d-%s", stepIndex, sanitizeFilename(stepName));
            return new ExecutionLogger(storageService, executionId + "/steps/" + stepDirName);
        } catch (IOException e) {
            logger.error("Failed to create step directory for {}: {}", executionId, e.getMessage());
            return this; // Fallback to parent logger
        }
    }
    
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
