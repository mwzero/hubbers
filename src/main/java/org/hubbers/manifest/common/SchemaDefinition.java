package org.hubbers.manifest.common;

import java.util.LinkedHashMap;
import java.util.Map;

public class SchemaDefinition {
    private String type;
    private Map<String, PropertyDefinition> properties = new LinkedHashMap<>();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, PropertyDefinition> getProperties() { return properties; }
    public void setProperties(Map<String, PropertyDefinition> properties) { this.properties = properties; }
}
