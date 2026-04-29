package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OllamaConfig {
    private String baseUrl;
    private String defaultModel;
    private Integer timeoutSeconds = 300; // Default 5 minutes

}
