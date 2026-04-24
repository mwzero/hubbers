package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a llama.cpp server (llama-server / llama-cpp-python).
 *
 * <p>llama.cpp exposes an OpenAI-compatible chat completions endpoint at
 * {@code /v1/chat/completions}. No API key is required by default when
 * running locally.</p>
 *
 * @since 0.1.0
 */
@Getter
@Setter
public class LlamaCppConfig {
    /** Base URL of the llama.cpp server (default: http://localhost:8080). */
    private String baseUrl = "http://localhost:8080";

    /** Optional API key if the server is configured with {@code --api-key}. */
    private String apiKey;

    /** Default model alias or path (sent as the {@code model} field). */
    private String defaultModel = "default";

    /** Request timeout in seconds (default: 300 — llama.cpp can be slow on CPU). */
    private Integer timeoutSeconds = 300;
}
