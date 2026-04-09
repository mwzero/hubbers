package org.hubbers.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAiConfig {
    private String apiKey;
    private String baseUrl;
    private String defaultModel;

}
