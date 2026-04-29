package org.hubbers.tool;

import lombok.Getter;

/**
 * Exception thrown when tool execution fails.
 * 
 * <p>Provides categorized error information to help diagnose issues
 * and enable error-specific handling (e.g., retry logic, UI feedback).</p>
 * 
 * <p>Error categories:
 * <ul>
 *   <li>{@link ErrorCategory#CONFIGURATION_ERROR} - Missing or invalid configuration</li>
 *   <li>{@link ErrorCategory#NETWORK_ERROR} - HTTP/network failures</li>
 *   <li>{@link ErrorCategory#VALIDATION_ERROR} - Schema or data validation failures</li>
 *   <li>{@link ErrorCategory#INTERNAL_ERROR} - Unexpected internal errors</li>
 * </ul>
 * 
 * @since 0.1.0
 */
@Getter
public class ToolExecutionException extends RuntimeException {
    
    private final String toolName;
    private final ErrorCategory category;
    
    /**
     * Create a new tool execution exception.
     * 
     * @param toolName the name of the tool that failed
     * @param message the error message
     * @param category the error category for classification
     */
    public ToolExecutionException(String toolName, String message, ErrorCategory category) {
        super(String.format("[%s] %s", toolName, message));
        this.toolName = toolName;
        this.category = category;
    }
    
    /**
     * Create a new tool execution exception with a cause.
     * 
     * @param toolName the name of the tool that failed
     * @param message the error message
     * @param category the error category for classification
     * @param cause the underlying cause
     */
    public ToolExecutionException(String toolName, String message, ErrorCategory category, Throwable cause) {
        super(String.format("[%s] %s", toolName, message), cause);
        this.toolName = toolName;
        this.category = category;
    }
    
    /**
     * Error categories for tool execution failures.
     * 
     * <p>Used to classify errors for better diagnostics and handling.</p>
     */
    public enum ErrorCategory {
        /**
         * Missing or invalid configuration (API keys, URLs, etc.).
         */
        CONFIGURATION_ERROR,
        
        /**
         * HTTP failures, timeouts, or network issues.
         */
        NETWORK_ERROR,
        
        /**
         * Schema validation failures or invalid input/output data.
         */
        VALIDATION_ERROR,
        
        /**
         * Unexpected internal errors (parsing failures, etc.).
         */
        INTERNAL_ERROR
    }
}
