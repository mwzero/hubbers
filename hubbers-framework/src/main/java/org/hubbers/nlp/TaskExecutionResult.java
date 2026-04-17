package org.hubbers.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.execution.ExecutionMetadata;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.ExecutionTrace;

import java.util.List;

/**
 * Result of a natural language task execution.
 * Contains the agent's output, reasoning, tools used, and execution metadata.
 */
public class TaskExecutionResult {
    private JsonNode result;
    private String reasoning;
    private List<String> toolsUsed;
    private int iterations;
    private ExecutionStatus status;
    private String error;
    private ExecutionMetadata metadata;
    private String conversationId;
    private ExecutionTrace executionTrace;

    public TaskExecutionResult() {
    }

    public static TaskExecutionResult success(JsonNode result, String reasoning, 
                                             List<String> toolsUsed, int iterations,
                                             String conversationId) {
        TaskExecutionResult taskResult = new TaskExecutionResult();
        taskResult.result = result;
        taskResult.reasoning = reasoning;
        taskResult.toolsUsed = toolsUsed;
        taskResult.iterations = iterations;
        taskResult.status = ExecutionStatus.SUCCESS;
        taskResult.conversationId = conversationId;
        return taskResult;
    }

    public static TaskExecutionResult failed(String error, String conversationId) {
        TaskExecutionResult taskResult = new TaskExecutionResult();
        taskResult.status = ExecutionStatus.FAILED;
        taskResult.error = error;
        taskResult.conversationId = conversationId;
        return taskResult;
    }

    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    // Getters and setters
    public JsonNode getResult() {
        return result;
    }

    public void setResult(JsonNode result) {
        this.result = result;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ExecutionMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ExecutionMetadata metadata) {
        this.metadata = metadata;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public ExecutionTrace getExecutionTrace() {
        return executionTrace;
    }

    public void setExecutionTrace(ExecutionTrace executionTrace) {
        this.executionTrace = executionTrace;
    }
}