package org.hubbers.manifest.agent;

import lombok.Data;

@Data
public class ModelConfig {
    private String provider;
    private String name;
    private Double temperature;
}
