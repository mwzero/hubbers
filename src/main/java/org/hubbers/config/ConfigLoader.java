package org.hubbers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {
    private final ObjectMapper yamlMapper = JacksonFactory.yamlMapper();
    private final String repoPath;

    public ConfigLoader() {
        this("repo");
    }

    public ConfigLoader(String repoPath) {
        this.repoPath = repoPath;
    }

    public AppConfig load() {
        Path configPath = Paths.get(repoPath, "application.yaml");
        
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("application.yaml not found at: " + configPath.toAbsolutePath());
        }
        
        try {
            AppConfig appConfig = yamlMapper.readValue(configPath.toFile(), AppConfig.class);
            // Override repoRoot with the actual repo path used
            appConfig.setRepoRoot(repoPath);
            resolveEnv(appConfig);
            return appConfig;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load application config from " + configPath, e);
        }
    }

    private void resolveEnv(AppConfig appConfig) {
        if (appConfig.getOpenai() != null) {
            OpenAiConfig openAi = appConfig.getOpenai();
            openAi.setApiKey(resolveValue(openAi.getApiKey()));
            openAi.setBaseUrl(resolveValue(openAi.getBaseUrl()));
            openAi.setDefaultModel(resolveValue(openAi.getDefaultModel()));
        }
        if (appConfig.getOllama() != null) {
            OllamaConfig ollama = appConfig.getOllama();
            ollama.setBaseUrl(resolveValue(ollama.getBaseUrl()));
            ollama.setDefaultModel(resolveValue(ollama.getDefaultModel()));
        }
        if (appConfig.getTools() != null) {
            ToolsConfig toolsConfig = appConfig.getTools();
            toolsConfig.getTools().forEach((toolType, config) -> {
                config.replaceAll((key, value) -> resolveValue(value));
            });
        }
        if (appConfig.getExecutions() != null) {
            ExecutionsConfig executions = appConfig.getExecutions();
            executions.setPath(resolveValue(executions.getPath()));
        }
    }

    private String resolveValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            String envName = value.substring(2, value.length() - 1);
            return System.getenv(envName);
        }
        return value;
    }
}
