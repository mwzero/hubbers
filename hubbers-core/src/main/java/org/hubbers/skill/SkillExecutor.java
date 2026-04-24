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
 * 
 * <p>Skills are reusable methodologies/instructions following the agentskills.io
 * specification. They can be executed in three modes:</p>
 * 
 * <ul>
 *   <li><b>llm-prompt</b>: Pass SKILL.md body as system prompt to LLM</li>
 *   <li><b>script</b>: Execute scripts from scripts/ directory</li>
 *   <li><b>hybrid</b>: Let LLM decide between instructions and scripts</li>
 * </ul>
 * 
 * <p>Skills differ from agents in that they don't define model configuration
 * and are meant to be invoked by agents or pipelines rather than executed directly.</p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * SkillManifest skill = repository.loadSkill(\"sentiment-analysis\");
 * JsonNode input = mapper.readTree(\"{\\\"text\\\":\\\"Great product!\\\"}\");
 * RunResult result = skillExecutor.execute(skill, input);
 * }</pre>
 * 
 * @see <a href="https://agentskills.io">agentskills.io specification</a>
 * @since 0.1.0
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
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid skill configuration for '{}': {}", manifest.getName(), e.getMessage());
            return RunResult.failed("Invalid configuration: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Skill execution failed: {}", manifest.getName(), e);
            return RunResult.failed("Execution error: " + e.getMessage());
        }
    }

    private RunResult executeLlmPrompt(SkillManifest manifest, JsonNode input) {
        // Get model provider - use skill config if available, otherwise default
        ModelProvider modelProvider;
        String modelName = null;
        Double temperature = null;
        
        if (manifest.hasModelConfig()) {
            SkillManifest.ModelConfig modelConfig = manifest.getModelConfig();
            String providerName = modelConfig.getProvider() != null ? modelConfig.getProvider() : "ollama";
            modelProvider = modelProviderRegistry.get(providerName);
            
            if (modelProvider == null) {
                log.warn("Model provider '{}' not found, falling back to default", providerName);
                modelProvider = getDefaultModelProvider();
            } else {
                log.debug("Using skill-specific model provider: {}", providerName);
            }
            
            modelName = modelConfig.getName();
            temperature = modelConfig.getTemperature();
            log.debug("Using skill-specific model config: name={}, temp={}", modelName, temperature);
        } else {
            modelProvider = getDefaultModelProvider();
            log.debug("No model config in skill, using default provider");
        }
        
        LlmPromptExecutionHandler handler = new LlmPromptExecutionHandler(modelProvider, mapper);
        return handler.execute(manifest, input, modelName, temperature);
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
