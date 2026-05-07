package org.hubbers.manifest.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.common.SchemaDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class OutputDefinition {
    private SchemaDefinition schema;

    @JsonProperty("type")
    public void setType(String type) {
        ensureSchema().setType(type);
    }

    @JsonProperty("properties")
    public void setProperties(Map<String, PropertyDefinition> properties) {
        ensureSchema().setProperties(new LinkedHashMap<>(properties));
    }

    private SchemaDefinition ensureSchema() {
        if (schema == null) {
            schema = new SchemaDefinition();
        }
        return schema;
    }
}
