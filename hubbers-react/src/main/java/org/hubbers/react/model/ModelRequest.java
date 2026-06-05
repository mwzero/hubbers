package org.hubbers.react.model;

import lombok.Data;

import java.util.List;

/**
 * Request object sent to a {@link ModelProvider}.
 *
 * <p>Supports two interaction modes:</p>
 * <ul>
 *   <li>Single-turn: set {@code systemPrompt} + {@code userPrompt}</li>
 *   <li>Multi-turn: set {@code messages} (replaces the above)</li>
 * </ul>
 *
 * <p>The optional {@code provider} field allows a routing {@link ModelProvider}
 * (e.g. {@code ModelProviderRegistry}) to select the correct underlying provider
 * when multiple providers are available.</p>
 */
@Data
public class ModelRequest {
    private String systemPrompt;
    private String userPrompt;
    private String model;
    private Double temperature;

    /** Top-level {@code think} field for Ollama thinking models (e.g. qwen3). */
    private Boolean think;

    /** Optional provider name used by a routing ModelProvider to select the backend. */
    private String provider;

    /** Functions available to the LLM (for function / tool calling). */
    private List<FunctionDefinition> functions;

    /** Full conversation history (multi-turn alternative to systemPrompt/userPrompt). */
    private List<Message> messages;
}
