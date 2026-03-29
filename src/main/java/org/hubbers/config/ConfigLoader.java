package org.hubbers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.io.InputStream;

public class ConfigLoader {
    private final ObjectMapper yamlMapper = JacksonFactory.yamlMapper();

    public AppConfig load() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            if (is == null) {
                throw new IllegalStateException("application.yaml not found");
            }
            AppConfig appConfig = yamlMapper.readValue(is, AppConfig.class);
            resolveEnv(appConfig);
            return appConfig;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load application config", e);
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
