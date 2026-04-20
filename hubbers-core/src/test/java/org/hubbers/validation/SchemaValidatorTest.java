package org.hubbers.validation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.common.SchemaDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void validatesArrayTypeSuccessfully() {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setType("object");

        PropertyDefinition items = new PropertyDefinition();
        items.setType("array");
        items.setRequired(true);

        schema.setProperties(new LinkedHashMap<>());
        schema.getProperties().put("items", items);

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.putArray("items").add("a").add("b");

        ValidationResult result = validator.validate(input, schema);
        assertTrue(result.isValid());
    }

    @Test
    void rejectsNonArrayWhenArrayExpected() {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setType("object");

        PropertyDefinition items = new PropertyDefinition();
        items.setType("array");
        items.setRequired(true);

        schema.setProperties(new LinkedHashMap<>());
        schema.getProperties().put("items", items);

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("items", "not-an-array");

        ValidationResult result = validator.validate(input, schema);
        assertFalse(result.isValid());
    }
}
