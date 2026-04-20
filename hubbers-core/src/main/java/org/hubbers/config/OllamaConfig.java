package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OllamaConfig {
    private String baseUrl;
    private String defaultModel;
    private Integer timeoutSeconds = 120; // Default 2 minutes

}
