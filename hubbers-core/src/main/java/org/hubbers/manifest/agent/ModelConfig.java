package org.hubbers.manifest.agent;

import lombok.Data;

@Data
public class ModelConfig {
    private String provider;
    private String name;
    private Double temperature;

    /**
     * Disables the thinking/reasoning chain for models that support it (e.g. qwen3).
     * Maps to the top-level {@code think} field in the Ollama API request.
     * Set to {@code false} to suppress reasoning and reduce latency significantly.
     */
    private Boolean think;
}
