package org.hubbers.mcp;

import lombok.extern.slf4j.Slf4j;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.mcp.protocol.McpContent;
import org.hubbers.mcp.protocol.McpPromptInfo;
import org.hubbers.mcp.protocol.McpPromptInfo.McpPromptArgument;
import org.hubbers.mcp.protocol.McpPromptResult;
import org.hubbers.mcp.protocol.McpPromptResult.McpPromptMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exposes Hubbers agent manifests as MCP prompts.
 * Each agent's system prompt and instructions become available as a reusable
 * prompt template in external chat UIs.
 */
@Slf4j
public class McpPromptProvider {

    private final ArtifactRepository artifactRepository;

    /**
     * Creates a new McpPromptProvider.
     *
     * @param artifactRepository the artifact repository for loading agent manifests
     */
    public McpPromptProvider(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    /**
     * Lists all available agent prompts.
     *
     * @return list of MCP prompt definitions
     */
    public List<McpPromptInfo> listPrompts() {
        List<McpPromptInfo> prompts = new ArrayList<>();

        for (String name : artifactRepository.listAgents()) {
            try {
                AgentManifest manifest = artifactRepository.loadAgent(name);
                convertAgent(manifest).ifPresent(prompts::add);
            } catch (Exception e) {
                log.warn("Failed to convert agent '{}' to MCP prompt: {}", name, e.getMessage());
            }
        }

        log.info("MCP prompts/list: {} prompts available", prompts.size());
        return prompts;
    }

    /**
     * Gets a specific prompt by name with optional argument substitution.
     *
     * @param name    the agent name
     * @param request optional request text to inject as user message
     * @return the prompt result, or empty if agent not found
     */
    public Optional<McpPromptResult> getPrompt(String name, String request) {
        try {
            AgentManifest manifest = artifactRepository.loadAgent(name);
            return Optional.of(buildPromptResult(manifest, request));
        } catch (Exception e) {
            log.warn("Failed to load prompt '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<McpPromptInfo> convertAgent(AgentManifest manifest) {
        String agentName = manifest.getAgent() != null ? manifest.getAgent().getName() : null;
        if (agentName == null) {
            return Optional.empty();
        }

        String description;
        if (manifest.getAgent().getDescription() != null) {
            description = manifest.getAgent().getDescription();
        } else {
            description = "Agent prompt: " + agentName;
        }

        List<McpPromptArgument> arguments = new ArrayList<>();
        arguments.add(McpPromptArgument.builder()
                .name("request")
                .description("The user request or task to perform")
                .required(true)
                .build());

        return Optional.of(McpPromptInfo.builder()
                .name(agentName)
                .description(description)
                .arguments(arguments)
                .build());
    }

    private McpPromptResult buildPromptResult(AgentManifest manifest, String request) {
        List<McpPromptMessage> messages = new ArrayList<>();

        // Add system prompt if available
        if (manifest.getInstructions() != null && manifest.getInstructions().getSystemPrompt() != null) {
            messages.add(McpPromptMessage.builder()
                    .role("assistant")
                    .content(McpContent.text(manifest.getInstructions().getSystemPrompt()))
                    .build());
        }

        // Add user request if provided
        if (request != null && !request.isBlank()) {
            messages.add(McpPromptMessage.builder()
                    .role("user")
                    .content(McpContent.text(request))
                    .build());
        }

        String description = manifest.getAgent() != null ? manifest.getAgent().getDescription() : null;

        return McpPromptResult.builder()
                .description(description)
                .messages(messages)
                .build();
    }
}
