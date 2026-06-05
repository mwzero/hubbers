package org.hubbers.react;

import java.util.List;

/**
 * Lean, platform-agnostic configuration for a single agent execution.
 *
 * <p>This record captures everything {@link HubberAgentLoop} needs to execute an
 * agent without knowing about {@code AgentManifest} or any other {@code hubbers-core}
 * type.  In a full-platform deployment, {@code AgentConfigAdapter} in
 * {@code hubbers-core} converts an {@code AgentManifest} into an {@code AgentConfig}.</p>
 *
 * @param name              agent name (used only for logging)
 * @param systemPrompt      fully-built system prompt including output schema hint (if any)
 * @param userPromptTemplate optional {@code {field}} template for the user message;
 *                           when {@code null} the raw JSON input is used as-is
 * @param model             LLM model identifier (e.g. {@code "llama3.2"}, {@code "gpt-4o"})
 * @param modelProvider     provider name passed to a routing {@code ModelProvider}
 *                          (e.g. {@code "ollama"}, {@code "openai"})
 * @param temperature       sampling temperature; {@code null} = use provider default
 * @param think             whether to enable thinking mode for supporting models (qwen3, etc.)
 * @param artifactNames     names of tools, agents, pipelines, or skills available to the loop
 * @param maxIterations     maximum number of ReAct iterations (0 = use default)
 * @param timeoutMs         execution timeout in milliseconds (0 = use default)
 * @param simpleMode        when {@code true}, a single LLM call is made (no tool loop)
 */
public record AgentConfig(
        String name,
        String systemPrompt,
        String userPromptTemplate,
        String model,
        String modelProvider,
        Double temperature,
        Boolean think,
        List<String> artifactNames,
        int maxIterations,
        long timeoutMs,
        boolean simpleMode) {

    /** Default maximum number of ReAct iterations when {@code maxIterations} is 0. */
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    /** Default execution timeout in milliseconds when {@code timeoutMs} is 0. */
    public static final long DEFAULT_TIMEOUT_MS = 60_000L;

    /**
     * Returns the effective max iterations: the configured value, or
     * {@link #DEFAULT_MAX_ITERATIONS} if zero or negative.
     *
     * @return effective maximum iterations
     */
    public int effectiveMaxIterations() {
        return maxIterations > 0 ? maxIterations : DEFAULT_MAX_ITERATIONS;
    }

    /**
     * Returns the effective timeout in milliseconds: the configured value, or
     * {@link #DEFAULT_TIMEOUT_MS} if zero or negative.
     *
     * @return effective timeout in milliseconds
     */
    public long effectiveTimeoutMs() {
        return timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }
}
