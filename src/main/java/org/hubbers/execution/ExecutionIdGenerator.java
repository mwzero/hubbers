package org.hubbers.execution;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generates unique execution IDs with timestamp-uuid format.
 * Format: yyyyMMdd_HHmmss-{short-uuid}
 * Example: 20260402_144523-a1b2c3d4
 */
public class ExecutionIdGenerator {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Generates a new execution ID with timestamp and short UUID.
     * Thread-safe.
     * 
     * @return execution ID in format yyyyMMdd_HHmmss-{short-uuid}
     */
    public static String generate() {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + shortUuid;
    }
    
    /**
     * Validates if a string follows the execution ID format.
     * 
     * @param executionId the string to validate
     * @return true if valid format, false otherwise
     */
    public static boolean isValid(String executionId) {
        if (executionId == null || executionId.isEmpty()) {
            return false;
        }
        // Format: yyyyMMdd_HHmmss-{8 chars}
        return executionId.matches("\\d{8}_\\d{6}-[a-f0-9]{8}");
    }
}
