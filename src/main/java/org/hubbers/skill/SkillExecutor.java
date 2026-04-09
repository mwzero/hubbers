package org.hubbers.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.execution.ExecutionMetadata;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.model.ModelProvider;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.skill.execution.HybridExecutionHandler;
import org.hubbers.skill.execution.LlmPromptExecutionHandler;
import org.hubbers.skill.execution.ScriptExecutionHandler;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main executor for skills with hybrid execution mode support.
 * Routes to appropriate handler based on skill's execution_mode metadata:
 * - llm-prompt: Pass SKILL.md body as system prompt to LLM
 * - script: Execute scripts from scripts/ directory
 * - hybrid: Let LLM decide between instructions and scripts
 */
public class SkillExecutor {
    private static final Logger log = LoggerFactory.getLogger(SkillExecutor.class);
    
    private final ModelProviderRegistry modelProviderRegistry;
    private final ToolExecutor toolExecutor;
    private final org.hubbers.app.ArtifactRepository artifactRepository;
    private final ObjectMapper mapper;
    private final SkillValidator validator;

    public SkillExecutor(ModelProviderRegistry modelProviderRegistry,
                         ToolExecutor toolExecutor,
                         org.hubbers.app.ArtifactRepository artifactRepository,
                         ObjectMapper mapper) {
        this.modelProviderRegistry = modelProviderRegistry;
        this.toolExecutor = toolExecutor;
        this.artifactRepository = artifactRepository;
        this.mapper = mapper;
        this.validator = new SkillValidator();
    }

    /**
     * Execute a skill with the given input.
     * Validates the skill, then routes to appropriate execution handler.
     * 
     * @param manifest Skill manifest
     * @param input User input as JSON
     * @return RunResult with execution status and output
     */
    public RunResult execute(SkillManifest manifest, JsonNode input) {
        long startTime = System.currentTimeMillis();
        
        log.info("Executing skill: {}", manifest.getName());

        // Validate skill
        SkillValidator.ValidationResult validation = validator.validate(manifest);
        if (!validation.isValid()) {
            log.error("Skill validation failed: {}", validation.getErrorMessage());
            return RunResult.failed("Skill validation failed: " + validation.getErrorMessage());
        }

        try {
            // Route to appropriate handler based on execution mode
            String executionMode = manifest.getExecutionMode();
            log.debug("Skill '{}' execution mode: {}", manifest.getName(), executionMode);

            RunResult result = switch (executionMode) {
                case "llm-prompt" -> executeLlmPrompt(manifest, input);
                case "script" -> executeScript(manifest, input);
                case "hybrid" -> executeHybrid(manifest, input);
                default -> RunResult.failed("Unknown execution mode: " + executionMode);
            };

            // Add execution metadata
            long endTime = System.currentTimeMillis();
            ExecutionMetadata metadata = new ExecutionMetadata();
            metadata.setStartedAt(startTime);
            metadata.setEndedAt(endTime);
            metadata.setDetails(String.format(
                "skill=%s, mode=%s, latencyMs=%d",
                manifest.getName(), executionMode, (endTime - startTime)
            ));
            result.setMetadata(metadata);

            return result;
        } catch (Exception e) {
            log.error("Skill execution failed: {}", manifest.getName(), e);
            return RunResult.failed("Execution error: " + e.getMessage());
        }
    }

    private RunResult executeLlmPrompt(SkillManifest manifest, JsonNode input) {
        // Use default model provider (Ollama or OpenAI based on config)
        ModelProvider modelProvider = getDefaultModelProvider();
        LlmPromptExecutionHandler handler = new LlmPromptExecutionHandler(modelProvider, mapper);
        return handler.execute(manifest, input);
    }

    private RunResult executeScript(SkillManifest manifest, JsonNode input) {
        ScriptExecutionHandler handler = new ScriptExecutionHandler(toolExecutor, artifactRepository, mapper);
        return handler.execute(manifest, input);
    }

    private RunResult executeHybrid(SkillManifest manifest, JsonNode input) {
        ModelProvider modelProvider = getDefaultModelProvider();
        HybridExecutionHandler handler = new HybridExecutionHandler(
            modelProvider, toolExecutor, artifactRepository, mapper
        );
        return handler.execute(manifest, input);
    }

    /**
     * Get default model provider for LLM-based execution.
     * Uses first available provider from registry.
     */
    private ModelProvider getDefaultModelProvider() {
        // Try Ollama first (common for local execution)
        ModelProvider provider = modelProviderRegistry.get("ollama");
        if (provider != null) {
            return provider;
        }

        // Fall back to OpenAI
        provider = modelProviderRegistry.get("openai");
        if (provider != null) {
            return provider;
        }

        throw new IllegalStateException(
            "No model provider available. Configure 'ollama' or 'openai' in application.yaml"
        );
    }
}
