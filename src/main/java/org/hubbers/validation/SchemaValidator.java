package org.hubbers.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.common.SchemaDefinition;

import java.util.Map;

public class SchemaValidator {

    public ValidationResult validate(JsonNode input, SchemaDefinition schema) {
        ValidationResult result = ValidationResult.ok();
        if (schema == null || schema.getProperties() == null) {
            return result;
        }
        for (Map.Entry<String, PropertyDefinition> entry : schema.getProperties().entrySet()) {
            String key = entry.getKey();
            PropertyDefinition property = entry.getValue();
            JsonNode value = input.get(key);
            if (property.isRequired() && (value == null || value.isNull())) {
                result.addError("Required field missing: " + key);
                continue;
            }
            if (value != null && !value.isNull() && !matchesType(value, property.getType())) {
                result.addError("Invalid type for field " + key + ", expected: " + property.getType());
            }
        }
        return result;
    }

    private boolean matchesType(JsonNode value, String type) {
        if (type == null) return true;
        return switch (type) {
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            default -> true;
        };
    }
}
