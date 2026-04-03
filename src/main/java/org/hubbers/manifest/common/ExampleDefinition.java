package org.hubbers.manifest.common;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ExampleDefinition {
    private String name;
    private String description;
    private JsonNode input;
    private JsonNode output;
}
