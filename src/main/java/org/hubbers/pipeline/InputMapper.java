package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public class InputMapper {
    private final ObjectMapper mapper;

    public InputMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode map(JsonNode initialInput, Map<String, String> mapping, PipelineState state) {
        if (mapping == null || mapping.isEmpty()) {
            return initialInput;
        }
        ObjectNode output = mapper.createObjectNode();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String targetField = entry.getKey();
            String sourceExpr = entry.getValue();
            JsonNode resolved = resolveExpression(sourceExpr, state);
            output.set(targetField, resolved == null ? mapper.nullNode() : resolved);
        }
        return output;
    }

    private JsonNode resolveExpression(String expression, PipelineState state) {
        if (expression == null || !expression.startsWith("${") || !expression.endsWith("}")) {
            return mapper.valueToTree(expression);
        }
        String path = expression.substring(2, expression.length() - 1);
        String[] parts = path.split("\\.");
        if (parts.length < 4 || !"steps".equals(parts[0]) || !"output".equals(parts[2])) {
            return mapper.nullNode();
        }
        JsonNode current = state.getStepOutput(parts[1]);
        for (int i = 3; i < parts.length; i++) {
            if (current == null) {
                return mapper.nullNode();
            }
            current = current.get(parts[i]);
        }
        return current;
    }
}
