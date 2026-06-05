package org.hubbers.react.model;

/**
 * Abstraction for calling a large language model.
 *
 * <p>Implementations may delegate to a single underlying provider (e.g. Ollama, OpenAI)
 * or act as a routing registry that picks the right provider based on
 * {@link ModelRequest#getProvider()}.</p>
 *
 * <p>This interface intentionally has no {@code providerName()} method so that it can
 * also be used as a {@code @FunctionalInterface} and passed as a lambda from
 * {@code hubbers-core} wiring code.</p>
 */
@FunctionalInterface
public interface ModelProvider {

    /**
     * Send a request to the underlying LLM and return its response.
     *
     * @param request the model request (prompt, messages, functions, etc.)
     * @return the model response
     */
    ModelResponse generate(ModelRequest request);
}
