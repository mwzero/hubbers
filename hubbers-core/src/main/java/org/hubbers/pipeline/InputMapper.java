package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputMapper {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
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
            JsonNode resolved = resolveExpression(sourceExpr, initialInput, state);
            output.set(targetField, resolved == null ? mapper.nullNode() : resolved);
        }
        return output;
    }

    private JsonNode resolveExpression(String expression, JsonNode initialInput, PipelineState state) {
        if (expression == null) {
            return mapper.nullNode();
        }

        if (isFullPlaceholder(expression)) {
            return resolvePlaceholder(expression.substring(2, expression.length() - 1), initialInput, state);
        }

        if (!expression.contains("${")) {
            return mapper.valueToTree(expression);
        }

        return mapper.valueToTree(interpolateString(expression, initialInput, state));
    }

    private boolean isFullPlaceholder(String expression) {
        return expression.startsWith("${")
            && expression.endsWith("}")
            && expression.indexOf("${", 2) < 0;
    }

    private JsonNode resolvePlaceholder(String path, JsonNode initialInput, PipelineState state) {
        String[] parts = path.split("\\.");
        if (parts.length >= 3 && "steps".equals(parts[0]) && "output".equals(parts[2])) {
            JsonNode current = state.getStepOutput(parts[1]);
            for (int i = 3; i < parts.length; i++) {
                if (current == null) {
                    return mapper.nullNode();
                }
                current = current.get(parts[i]);
            }
            return current;
        }
        JsonNode current = initialInput;
        for (String part : parts) {
            if (current == null) {
                return mapper.nullNode();
            }
            current = current.get(part);
        }
        return current;
    }

    private String interpolateString(String expression, JsonNode initialInput, PipelineState state) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            JsonNode resolved = resolvePlaceholder(matcher.group(1), initialInput, state);
            String replacement = stringify(resolved);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String stringify(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.toString();
    }
}
