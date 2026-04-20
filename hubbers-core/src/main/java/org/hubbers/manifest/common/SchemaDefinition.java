package org.hubbers.manifest.common;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SchemaDefinition {
    private String type;
    private Map<String, PropertyDefinition> properties = new LinkedHashMap<>();
}
