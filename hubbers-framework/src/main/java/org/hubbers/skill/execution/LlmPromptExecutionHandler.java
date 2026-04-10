package org.hubbers.skill.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.model.ModelProvider;
import org.hubbers.model.ModelRequest;
import org.hubbers.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes skills in llm-prompt mode.
 * Passes SKILL.md body as system prompt to LLM with user input.
 */
public class LlmPromptExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(LlmPromptExecutionHandler.class);
    
    private final ModelProvider modelProvider;
    private final ObjectMapper mapper;

    public LlmPromptExecutionHandler(ModelProvider modelProvider, ObjectMapper mapper) {
        this.modelProvider = modelProvider;
        this.mapper = mapper;
    }

    /**
     * Execute skill by passing instructions to LLM.
     * 
     * @param manifest Skill manifest containing instructions in body
     * @param input User input as JSON
     * @return RunResult with LLM response
     */
    public RunResult execute(SkillManifest manifest, JsonNode input) {
        return execute(manifest, input, null, null);
    }

    /**
     * Execute skill by passing instructions to LLM with specific model configuration.
     * 
     * @param manifest Skill manifest containing instructions in body
     * @param input User input as JSON  
     * @param modelName Specific model name to use (optional, uses provider default if null)
     * @param temperature Temperature setting (optional, uses 0.1 if null)
     * @return RunResult with LLM response
     */
    public RunResult execute(SkillManifest manifest, JsonNode input, String modelName, Double temperature) {
        log.debug("Executing skill '{}' in llm-prompt mode", manifest.getName());
        if (modelName != null) {
            log.debug("Using model: {} with temperature: {}", modelName, temperature);
        }

        // Build model request with skill instructions as system prompt
        ModelRequest request = buildModelRequest(manifest, input, modelName, temperature);

        try {
            // Call LLM
            ModelResponse response = modelProvider.generate(request);
            log.debug("LLM response received for skill '{}': latencyMs={}", 
                manifest.getName(), response.getLatencyMs());

            // Parse response as JSON if possible, otherwise wrap as text
            JsonNode output = parseResponse(response.getContent());

            return RunResult.success(output);
        } catch (Exception e) {
            log.error("LLM execution failed for skill '{}'", manifest.getName(), e);
            return RunResult.failed("LLM execution error: " + e.getMessage());
        }
    }

    private ModelRequest buildModelRequest(SkillManifest manifest, JsonNode input, String modelName, Double temperature) {
        // System prompt = skill instructions from SKILL.md body
        String systemPrompt = manifest.getBody();

        // User message = input as formatted JSON
        String userMessage = "Input:\n" + input.toPrettyString();

        ModelRequest request = new ModelRequest();
        request.setSystemPrompt(systemPrompt);
        request.setUserPrompt(userMessage);
        request.setModel(modelName); // Will use provider default if null
        request.setTemperature(temperature != null ? temperature : 0.1); // Default conservative temperature

        return request;
    }

    private JsonNode parseResponse(String content) {
        try {
            return mapper.readTree(content);
        } catch (Exception e) {
            // If not valid JSON, wrap as text response
            return mapper.createObjectNode().put("result", content);
        }
    }
}
