package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the Azure OpenAI service.
 *
 * @since 0.1.0
 */
@Getter
@Setter
public class AzureOpenAiConfig {
    /** The Azure OpenAI resource endpoint (e.g., https://my-resource.openai.azure.com). */
    private String endpoint;

    /** The Azure OpenAI API key. */
    private String apiKey;

    /** The deployment name (model deployment ID). */
    private String deployment;

    /** The API version to use (default: 2024-02-01). */
    private String apiVersion = "2024-02-01";

    /** Default model name for logging/display purposes. */
    private String defaultModel;
}
