package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConditionEvaluator} — expression evaluation against pipeline state.
 */
@DisplayName("ConditionEvaluator Tests")
class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;
    private ObjectMapper mapper;
    private PipelineState state;
    private JsonNode emptyInput;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
        mapper = JacksonFactory.jsonMapper();
        state = new PipelineState();
        emptyInput = mapper.createObjectNode();
    }

    @Test
    @DisplayName("Null condition should return true")
    void testEvaluate_NullCondition_ReturnsTrue() {
        assertTrue(evaluator.evaluate(null, state, emptyInput));
    }

    @Test
    @DisplayName("Blank condition should return true")
    void testEvaluate_BlankCondition_ReturnsTrue() {
        assertTrue(evaluator.evaluate("   ", state, emptyInput));
    }

    @Test
    @DisplayName("Numeric greater-than comparison with step output")
    void testEvaluate_NumericGreaterThan_FromStepOutput() {
        ObjectNode fetchOutput = mapper.createObjectNode();
        fetchOutput.put("count", 5);
        state.putStepOutput("fetch", fetchOutput);

        assertTrue(evaluator.evaluate("${steps.fetch.output.count} > 0", state, emptyInput));
        assertFalse(evaluator.evaluate("${steps.fetch.output.count} > 10", state, emptyInput));
    }

    @Test
    @DisplayName("Numeric equality comparison")
    void testEvaluate_NumericEquality() {
        ObjectNode output = mapper.createObjectNode();
        output.put("status_code", 200);
        state.putStepOutput("http_call", output);

        assertTrue(evaluator.evaluate("${steps.http_call.output.status_code} == 200", state, emptyInput));
        assertFalse(evaluator.evaluate("${steps.http_call.output.status_code} != 200", state, emptyInput));
    }

    @Test
    @DisplayName("String equality comparison")
    void testEvaluate_StringEquality() {
        ObjectNode output = mapper.createObjectNode();
        output.put("status", "ready");
        state.putStepOutput("check", output);

        assertTrue(evaluator.evaluate("${steps.check.output.status} == ready", state, emptyInput));
        assertFalse(evaluator.evaluate("${steps.check.output.status} == pending", state, emptyInput));
    }

    @Test
    @DisplayName("Boolean truthy values")
    void testEvaluate_BooleanTruthy() {
        assertTrue(evaluator.evaluate("true", state, emptyInput));
        assertFalse(evaluator.evaluate("false", state, emptyInput));
        assertFalse(evaluator.evaluate("0", state, emptyInput));
        assertTrue(evaluator.evaluate("1", state, emptyInput));
    }

    @Test
    @DisplayName("Missing step output resolves to empty string")
    void testEvaluate_MissingStepOutput_ResolvesToEmpty() {
        // Empty string is falsy
        assertFalse(evaluator.evaluate("${steps.nonexistent.output.field}", state, emptyInput));
    }

    @Test
    @DisplayName("Resolves from initial input when not step reference")
    void testEvaluate_ResolvesFromInput() {
        ObjectNode input = mapper.createObjectNode();
        input.put("enabled", "true");

        assertTrue(evaluator.evaluate("${enabled} == true", state, input));
    }
}
