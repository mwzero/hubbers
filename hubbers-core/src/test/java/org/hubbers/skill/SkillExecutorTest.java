package org.hubbers.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.skill.SkillFrontmatter;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.model.*;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkillExecutor — llm-prompt, script, and hybrid execution modes.
 * Uses mock ModelProvider to avoid external dependencies.
 */
@DisplayName("SkillExecutor Tests")
class SkillExecutorTest {

    private ObjectMapper mapper;
    private SkillExecutor skillExecutor;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();

        // Create mock model provider that returns valid JSON
        ModelProvider mockProvider = new ModelProvider() {
            @Override
            public String providerName() {
                return "ollama";
            }

            @Override
            public ModelResponse generate(ModelRequest request) {
                ModelResponse response = new ModelResponse();
                response.setContent("""
                        {"sentiment": "positive", "confidence": 0.95, "score": 0.8, "reasoning": "Positive language", "keywords": ["great"]}
                        """);
                response.setModel("test-model");
                response.setLatencyMs(10);
                response.setFinishReason("stop");
                return response;
            }
        };

        var modelRegistry = new ModelProviderRegistry(List.of(mockProvider));
        var toolExecutor = new ToolExecutor(List.of(), new SchemaValidator());

        skillExecutor = new SkillExecutor(
                modelRegistry,
                toolExecutor,
                null, // repository not needed for unit tests
                mapper
        );
    }

    @Test
    @DisplayName("Should execute llm-prompt mode skill successfully")
    void testExecute_LlmPromptMode_ReturnsSuccess() {
        // Given
        SkillManifest manifest = createTestSkill("test-sentiment", "llm-prompt",
                "# Sentiment Analysis\nAnalyze the sentiment of the provided text.",
                "ollama", "qwen2.5-coder:7b", 0.2);

        JsonNode input = mapper.createObjectNode().put("text", "This product is great!");

        // When
        RunResult result = skillExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "LLM-prompt skill should succeed");
        assertNotNull(result.getOutput(), "Output should not be null");
    }

    @Test
    @DisplayName("Should fail for unknown execution mode")
    void testExecute_UnknownMode_ReturnsFailed() {
        // Given
        SkillManifest manifest = createTestSkill("test-unknown", "unknown-mode",
                "Some instructions", "ollama", "qwen2.5-coder:7b", 0.5);

        JsonNode input = mapper.createObjectNode().put("text", "test");

        // When
        RunResult result = skillExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.FAILED, result.getStatus(),
                "Unknown execution mode should fail");
    }

    @Test
    @DisplayName("Should fail validation for skill with missing name")
    void testExecute_MissingName_FailsValidation() {
        // Given — skill with no name
        SkillManifest manifest = new SkillManifest();
        SkillFrontmatter frontmatter = new SkillFrontmatter();
        // name not set
        frontmatter.setDescription("A test skill");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("execution_mode", "llm-prompt");
        frontmatter.setMetadata(metadata);
        manifest.setFrontmatter(frontmatter);
        manifest.setBody("Some instructions");

        JsonNode input = mapper.createObjectNode().put("text", "test");

        // When
        RunResult result = skillExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.FAILED, result.getStatus(),
                "Skill with missing name should fail validation");
    }

    @Test
    @DisplayName("Should include execution metadata with timing info")
    void testExecute_LlmPromptMode_IncludesMetadata() {
        // Given
        SkillManifest manifest = createTestSkill("test-metadata", "llm-prompt",
                "Analyze text.", "ollama", "qwen2.5-coder:7b", 0.2);

        JsonNode input = mapper.createObjectNode().put("text", "Test input");

        // When
        RunResult result = skillExecutor.execute(manifest, input);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getMetadata(), "Execution metadata should be present");
        assertTrue(result.getMetadata().getStartedAt() > 0, "Start time should be set");
        assertTrue(result.getMetadata().getEndedAt() >= result.getMetadata().getStartedAt(),
                "End time should be >= start time");
    }

    // --- Helper methods ---

    private SkillManifest createTestSkill(String name, String executionMode,
                                           String body, String provider,
                                           String modelName, double temperature) {
        SkillManifest manifest = new SkillManifest();

        SkillFrontmatter frontmatter = new SkillFrontmatter();
        frontmatter.setName(name);
        frontmatter.setDescription("Test skill: " + name);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("execution_mode", executionMode);
        frontmatter.setMetadata(metadata);
        manifest.setFrontmatter(frontmatter);

        manifest.setBody(body);

        SkillManifest.ModelConfig modelConfig = new SkillManifest.ModelConfig();
        modelConfig.setProvider(provider);
        modelConfig.setName(modelName);
        modelConfig.setTemperature(temperature);
        manifest.setModelConfig(modelConfig);

        return manifest;
    }
}
