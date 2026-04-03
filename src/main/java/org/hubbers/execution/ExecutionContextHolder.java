package org.hubbers.execution;

/**
 * Thread-local holder for execution context.
 * Allows nested executors (like PipelineExecutor) to access the current execution context.
 */
public class ExecutionContextHolder {
    
    private static final ThreadLocal<ExecutionContext> contextHolder = new ThreadLocal<>();
    
    /**
     * Sets the execution context for the current thread.
     */
    public static void set(ExecutionContext context) {
        contextHolder.set(context);
    }
    
    /**
     * Gets the execution context for the current thread.
     * Returns null if no context is set.
     */
    public static ExecutionContext get() {
        return contextHolder.get();
    }
    
    /**
     * Clears the execution context for the current thread.
     * Should be called in a finally block to prevent memory leaks.
     */
    public static void clear() {
        contextHolder.remove();
    }
    
    /**
     * Checks if an execution context is currently set.
     */
    public static boolean hasContext() {
        return contextHolder.get() != null;
    }
}
