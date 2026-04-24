package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the Anthropic Claude API.
 *
 * @since 0.1.0
 */
@Getter
@Setter
public class AnthropicConfig {
    /** The Anthropic API key. */
    private String apiKey;

    /** The base URL for the Anthropic API (default: https://api.anthropic.com). */
    private String baseUrl = "https://api.anthropic.com";

    /** Default model name (e.g., claude-sonnet-4-20250514). */
    private String defaultModel = "claude-sonnet-4-20250514";

    /** API version header value (default: 2023-06-01). */
    private String apiVersion = "2023-06-01";

    /** Maximum tokens for output (default: 4096). */
    private Integer maxTokens = 4096;
}
