package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppConfig {
    private String repoRoot;
    private OpenAiConfig openai;
    private OllamaConfig ollama;
    private ToolsConfig tools;
    private ExecutionsConfig executions;

}
