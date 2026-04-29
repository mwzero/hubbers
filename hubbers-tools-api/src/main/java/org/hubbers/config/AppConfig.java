package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppConfig {
    private String repoRoot;
    private OpenAiConfig openai;
    private OllamaConfig ollama;
    private AzureOpenAiConfig azureOpenai;
    private AnthropicConfig anthropic;
    private LlamaCppConfig llamaCpp;
    private VectorDbConfig vectorDb;
    private ToolsConfig tools;
    private ExecutionsConfig executions;
    private SecurityConfig security;

}
