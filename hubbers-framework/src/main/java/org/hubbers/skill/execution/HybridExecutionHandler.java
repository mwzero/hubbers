package org.hubbers.skill.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.model.ModelProvider;
import org.hubbers.model.ModelRequest;
import org.hubbers.model.ModelResponse;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes skills in hybrid mode.
 * LLM receives both instructions AND script descriptions, then decides
 * whether to follow instructions or invoke scripts based on the task.
 */
public class HybridExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(HybridExecutionHandler.class);
    
    private final ModelProvider modelProvider;
    private final ToolExecutor toolExecutor;
    private final org.hubbers.app.ArtifactRepository artifactRepository;
    private final ObjectMapper mapper;

    public HybridExecutionHandler(ModelProvider modelProvider, 
                                   ToolExecutor toolExecutor,
                                   org.hubbers.app.ArtifactRepository artifactRepository,
                                   ObjectMapper mapper) {
        this.modelProvider = modelProvider;
        this.toolExecutor = toolExecutor;
        this.artifactRepository = artifactRepository;
        this.mapper = mapper;
    }

    /**
     * Execute skill in hybrid mode.
     * LLM decides whether to follow instructions or invoke scripts.
     * 
     * @param manifest Skill manifest with instructions and scripts
     * @param input User input as JSON
     * @return RunResult based on LLM's decision
     */
    public RunResult execute(SkillManifest manifest, JsonNode input) {
        log.debug("Executing skill '{}' in hybrid mode", manifest.getName());

        // Build enhanced prompt with both instructions and script options
        ModelRequest request = buildHybridRequest(manifest, input);

        try {
            // Let LLM decide the approach
            ModelResponse response = modelProvider.generate(request);
            log.debug("LLM decision received for skill '{}': latencyMs={}", 
                manifest.getName(), response.getLatencyMs());

            // Parse LLM's decision
            String decision = response.getContent();
            
            // Check if LLM decided to use scripts
            if (shouldUseScript(decision)) {
                log.debug("LLM chose to execute script for skill '{}'", manifest.getName());
                return new ScriptExecutionHandler(toolExecutor, artifactRepository, mapper).execute(manifest, input);
            } else {
                log.debug("LLM chose to follow instructions for skill '{}'", manifest.getName());
                // Parse response as JSON
                JsonNode output = parseResponse(decision);
                return RunResult.success(output);
            }
        } catch (Exception e) {
            log.error("Hybrid execution failed for skill '{}'", manifest.getName(), e);
            return RunResult.failed("Hybrid execution error: " + e.getMessage());
        }
    }

    private ModelRequest buildHybridRequest(SkillManifest manifest, JsonNode input) {
        StringBuilder systemPrompt = new StringBuilder();
        
        // Add skill instructions
        systemPrompt.append("# Skill Instructions\n\n");
        systemPrompt.append(manifest.getBody());
        systemPrompt.append("\n\n");

        // Add script availability info if scripts exist
        if (manifest.hasScripts()) {
            systemPrompt.append("# Available Scripts\n\n");
            systemPrompt.append("You have access to the following scripts:\n");
            manifest.getScripts().forEach(script -> {
                systemPrompt.append("- ").append(script.getFileName()).append("\n");
            });
            systemPrompt.append("\n");
            systemPrompt.append("**Decision**: You can either:\n");
            systemPrompt.append("1. Follow the instructions above and generate a response directly\n");
            systemPrompt.append("2. Use the available scripts by responding with: USE_SCRIPT\n");
            systemPrompt.append("\nChoose the most appropriate approach based on the input complexity.\n");
        }

        // User message with input
        String userMessage = "Input:\n" + input.toPrettyString();

        ModelRequest request = new ModelRequest();
        request.setSystemPrompt(systemPrompt.toString());
        request.setUserPrompt(userMessage);
        request.setTemperature(0.1);

        return request;
    }

    private boolean shouldUseScript(String llmResponse) {
        // Check if LLM explicitly chose to use scripts
        return llmResponse.trim().toUpperCase().startsWith("USE_SCRIPT");
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
