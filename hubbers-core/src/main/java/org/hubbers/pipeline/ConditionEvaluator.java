package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates simple condition expressions against pipeline state.
 *
 * <p>Supports a minimal expression language for step conditions:
 * <ul>
 *   <li>Variable references: {@code ${steps.X.output.Y}}</li>
 *   <li>Comparisons: {@code ==}, {@code !=}, {@code >}, {@code <}, {@code >=}, {@code <=}</li>
 *   <li>Literal values: numbers, quoted strings, {@code true}, {@code false}</li>
 * </ul>
 *
 * <p>Example: {@code "${steps.fetch.output.count} > 0"}
 *
 * @see org.hubbers.manifest.pipeline.PipelineStep#getCondition()
 */
@Slf4j
public class ConditionEvaluator {

    private static final Pattern COMPARISON =
            Pattern.compile("^\\s*(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Evaluates a condition expression against the current pipeline state.
     *
     * @param condition the expression string (may be {@code null} or blank)
     * @param state     the current pipeline state with step outputs
     * @param input     the original pipeline input
     * @return {@code true} if the condition is met or absent; {@code false} if it evaluates to false
     */
    public boolean evaluate(String condition, PipelineState state, JsonNode input) {
        if (condition == null || condition.isBlank()) {
            return true; // no condition means always execute
        }

        String resolved = resolvePlaceholders(condition, state, input);
        log.debug("Condition '{}' resolved to '{}'", condition, resolved);

        // Check for comparison operator
        Matcher matcher = COMPARISON.matcher(resolved);
        if (matcher.matches()) {
            String left = matcher.group(1).trim();
            String op = matcher.group(2);
            String right = matcher.group(3).trim();
            return compare(left, op, right);
        }

        // Treat as boolean literal
        return isTruthy(resolved.trim());
    }

    private String resolvePlaceholders(String expression, PipelineState state, JsonNode input) {
        Matcher matcher = PLACEHOLDER.matcher(expression);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            String value = resolvePath(path, state, input);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolvePath(String path, PipelineState state, JsonNode input) {
        String[] parts = path.split("\\.");
        if (parts.length >= 3 && "steps".equals(parts[0]) && "output".equals(parts[2])) {
            JsonNode current = state.getStepOutput(parts[1]);
            for (int i = 3; i < parts.length && current != null; i++) {
                current = current.get(parts[i]);
            }
            return current != null ? current.asText() : "";
        }
        // Resolve from initial input
        JsonNode current = input;
        for (String part : parts) {
            if (current == null) return "";
            current = current.get(part);
        }
        return current != null ? current.asText() : "";
    }

    private boolean compare(String left, String op, String right) {
        // Strip quotes from string literals
        left = stripQuotes(left);
        right = stripQuotes(right);

        // Try numeric comparison
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case ">"  -> l > r;
                case "<"  -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                default   -> false;
            };
        } catch (NumberFormatException ignored) {
            // Fall through to string comparison
        }

        // String comparison
        return switch (op) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default -> {
                log.warn("Cannot apply operator '{}' to non-numeric values: '{}', '{}'", op, left, right);
                yield false;
            }
        };
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private boolean isTruthy(String value) {
        if (value.isEmpty() || "false".equalsIgnoreCase(value) || "0".equals(value) || "null".equalsIgnoreCase(value)) {
            return false;
        }
        return true;
    }
}
