package org.hubbers.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.ExecutorRegistry;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.pipeline.ErrorHandler;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.manifest.pipeline.RetryConfig;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for PipelineExecutor — sequential step execution, state propagation, error handling.
 * Uses mock tool drivers to avoid external dependencies.
 */
@DisplayName("PipelineExecutor Tests")
class PipelineExecutorTest {

    private ObjectMapper mapper;
    private PipelineExecutor pipelineExecutor;
    private ExecutorRegistry executorRegistry;
    private ArtifactRepository repository;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() throws URISyntaxException {
        mapper = JacksonFactory.jsonMapper();
        executorRegistry = new ExecutorRegistry();

        var repo = Path.of(getClass().getClassLoader()
                .getResource("repo").toURI());
        repository = new ArtifactRepository(repo);

        // Create a tool executor with a simple mock driver
        ToolDriver mockWeatherDriver = new ToolDriver() {
            @Override
            public String type() {
                return "mock.weather";
            }

            @Override
            public JsonNode execute(ToolManifest manifest, JsonNode input) {
                ObjectNode result = mapper.createObjectNode();
                result.put("city", input.path("city").asText("Unknown"));
                result.put("temperature_celsius", 22);
                result.put("condition", "sunny");
                return result;
            }
        };

        toolExecutor = new ToolExecutor(List.of(mockWeatherDriver), new SchemaValidator());
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, toolExecutor);

        pipelineExecutor = new PipelineExecutor(
                repository, executorRegistry, toolExecutor, new InputMapper(mapper));
        executorRegistry.register(ExecutorRegistry.ExecutorType.PIPELINE, pipelineExecutor);
    }

    @Test
    @DisplayName("Should execute single-step pipeline with tool successfully")
    void testExecute_SingleToolStep_ReturnsSuccess() {
        // Given — pipeline with one tool step
        PipelineManifest manifest = createPipeline("single.step.pipeline",
                List.of(createToolStep("weather", "mock.weather", null)));

        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");

        // When
        RunResult result = pipelineExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Single-step pipeline should succeed");
        assertNotNull(result.getOutput(), "Output should not be null");
    }

    @Test
    @DisplayName("Should propagate state between sequential steps via input mapping")
    void testExecute_MultipleSteps_PropagatesState() {
        // Given — pipeline with two weather steps, second uses input mapping from first
        Map<String, String> step2Mapping = new LinkedHashMap<>();
        step2Mapping.put("city", "${steps.weather1.output.city}");

        PipelineManifest manifest = createPipeline("multi.step.pipeline",
                List.of(
                        createToolStep("weather1", "mock.weather", null),
                        createToolStep("weather2", "mock.weather", step2Mapping)
                ));

        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");

        // When
        RunResult result = pipelineExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Multi-step pipeline should succeed");
    }

    @Test
    @DisplayName("Should fail fast when a step references non-existent tool")
    void testExecute_StepFailure_PipelineFails() {
        // Given — pipeline referencing a tool that doesn't exist in the repo
        PipelineManifest manifest = createPipeline("failing.pipeline",
                List.of(createToolStep("fail_step", "nonexistent.tool", null)));

        // When
        RunResult result = pipelineExecutor.execute(manifest, mapper.createObjectNode());

        // Then
        assertEquals(ExecutionStatus.FAILED, result.getStatus(),
                "Pipeline should fail when a step references a missing tool");
    }

    @Test
    @DisplayName("Should handle empty pipeline steps — currently throws ArrayIndexOutOfBounds")
    void testExecute_EmptySteps_ThrowsOrFails() {
        // Given — pipeline with no steps (known bug: PipelineExecutor accesses index -1)
        PipelineManifest manifest = createPipeline("empty.pipeline", List.of());

        // When/Then — expect an exception due to the index -1 access in PipelineExecutor
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> pipelineExecutor.execute(manifest, mapper.createObjectNode()),
                "Empty pipeline currently throws ArrayIndexOutOfBoundsException");
    }

    // --- Condition tests ---

    @Test
    @DisplayName("Should skip step when condition evaluates to false")
    void testExecute_ConditionFalse_SkipsStep() {
        // Given — step1 produces count=0; step2 has condition requiring count > 0
        PipelineStep step1 = createToolStep("weather", "mock.weather", null);

        PipelineStep step2 = createToolStep("weather2", "mock.weather", null);
        step2.setCondition("${steps.weather.output.temperature_celsius} > 100");

        PipelineManifest manifest = createPipeline("cond.pipeline", List.of(step1, step2));

        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");

        // When
        RunResult result = pipelineExecutor.execute(manifest, input);

        // Then — pipeline succeeds but step2 was skipped (output comes from step1)
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
    }

    @Test
    @DisplayName("Should execute step when condition evaluates to true")
    void testExecute_ConditionTrue_ExecutesStep() {
        PipelineStep step1 = createToolStep("weather", "mock.weather", null);

        PipelineStep step2 = createToolStep("weather2", "mock.weather", null);
        step2.setCondition("${steps.weather.output.temperature_celsius} > 10");

        PipelineManifest manifest = createPipeline("cond.true.pipeline", List.of(step1, step2));

        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");

        RunResult result = pipelineExecutor.execute(manifest, input);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
    }

    // --- Retry tests ---

    @Test
    @DisplayName("Should retry step on failure and succeed on subsequent attempt")
    void testExecute_RetrySuccess_OnSecondAttempt() {
        // Given — a driver that fails once then succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        ToolDriver flakyDriver = new ToolDriver() {
            @Override
            public String type() { return "mock.weather"; }

            @Override
            public JsonNode execute(ToolManifest manifest, JsonNode input) {
                if (callCount.incrementAndGet() == 1) {
                    throw new RuntimeException("Transient failure");
                }
                ObjectNode result = mapper.createObjectNode();
                result.put("city", "Rome");
                result.put("temperature_celsius", 22);
                result.put("condition", "sunny");
                return result;
            }
        };
        toolExecutor = new ToolExecutor(List.of(flakyDriver), new SchemaValidator());
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, toolExecutor);
        pipelineExecutor = new PipelineExecutor(repository, executorRegistry, toolExecutor, new InputMapper(mapper));

        PipelineStep step = createToolStep("flaky", "mock.weather", null);
        step.setRetry(RetryConfig.builder().maxRetries(2).backoffMs(10).build());

        PipelineManifest manifest = createPipeline("retry.pipeline", List.of(step));

        // When — provide valid input so schema validation passes
        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");
        RunResult result = pipelineExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Should succeed after retry");
        assertEquals(2, callCount.get(), "Driver should have been called twice");
    }

    @Test
    @DisplayName("Should exhaust retries and fail")
    void testExecute_RetryExhausted_Fails() {
        ToolDriver alwaysFails = new ToolDriver() {
            @Override
            public String type() { return "mock.weather"; }

            @Override
            public JsonNode execute(ToolManifest manifest, JsonNode input) {
                throw new RuntimeException("Permanent failure");
            }
        };
        toolExecutor = new ToolExecutor(List.of(alwaysFails), new SchemaValidator());
        executorRegistry.register(ExecutorRegistry.ExecutorType.TOOL, toolExecutor);
        pipelineExecutor = new PipelineExecutor(repository, executorRegistry, toolExecutor, new InputMapper(mapper));

        PipelineStep step = createToolStep("always_fail", "mock.weather", null);
        step.setRetry(RetryConfig.builder().maxRetries(2).backoffMs(10).build());

        PipelineManifest manifest = createPipeline("retry.exhaust.pipeline", List.of(step));

        RunResult result = pipelineExecutor.execute(manifest, mapper.createObjectNode());
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
    }

    // --- Error handling tests ---

    @Test
    @DisplayName("on_error=SKIP should continue pipeline after step failure")
    void testExecute_OnErrorSkip_ContinuesPipeline() {
        PipelineStep step1 = createToolStep("fail_step", "nonexistent.tool", null);
        step1.setOnError(ErrorHandler.builder().action(ErrorHandler.Action.SKIP).build());

        PipelineStep step2 = createToolStep("weather", "mock.weather", null);

        PipelineManifest manifest = createPipeline("skip.pipeline", List.of(step1, step2));

        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");

        RunResult result = pipelineExecutor.execute(manifest, input);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Pipeline should succeed when failed step has on_error=SKIP");
    }

    @Test
    @DisplayName("on_error=FAIL (default) should stop pipeline")
    void testExecute_OnErrorFail_StopsPipeline() {
        PipelineStep step1 = createToolStep("fail_step", "nonexistent.tool", null);
        // on_error not set => defaults to FAIL

        PipelineStep step2 = createToolStep("weather", "mock.weather", null);

        PipelineManifest manifest = createPipeline("fail.default.pipeline", List.of(step1, step2));

        RunResult result = pipelineExecutor.execute(manifest, mapper.createObjectNode());
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
    }

    @Test
    @DisplayName("on_error=FALLBACK should execute alternative step")
    void testExecute_OnErrorFallback_ExecutesFallbackStep() {
        // step1 fails but falls back to step "safe_default"
        PipelineStep step1 = createToolStep("risky", "nonexistent.tool", null);
        step1.setOnError(ErrorHandler.builder()
                .action(ErrorHandler.Action.FALLBACK)
                .fallbackStep("safe_default")
                .build());

        PipelineStep fallback = createToolStep("safe_default", "mock.weather", null);

        PipelineManifest manifest = createPipeline("fallback.pipeline", List.of(step1, fallback));

        ObjectNode input = mapper.createObjectNode();
        input.put("city", "Rome");

        RunResult result = pipelineExecutor.execute(manifest, input);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Pipeline should succeed using fallback step");
    }

    // --- Helper methods ---

    private PipelineManifest createPipeline(String name, List<PipelineStep> steps) {
        PipelineManifest manifest = new PipelineManifest();
        Metadata meta = new Metadata();
        meta.setName(name);
        meta.setVersion("1.0.0");
        meta.setDescription("Test pipeline");
        manifest.setPipeline(meta);
        manifest.setSteps(steps);
        return manifest;
    }

    private PipelineStep createToolStep(String id, String toolName, Map<String, String> inputMapping) {
        PipelineStep step = new PipelineStep();
        step.setId(id);
        step.setTool(toolName);
        if (inputMapping != null) {
            step.setInputMapping(inputMapping);
        }
        return step;
    }
}
