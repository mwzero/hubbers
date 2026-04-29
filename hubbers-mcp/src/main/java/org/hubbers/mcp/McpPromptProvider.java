package org.hubbers.mcp;

import lombok.extern.slf4j.Slf4j;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.hubbers.mcp.protocol.McpContent;
import org.hubbers.mcp.protocol.McpPromptInfo;
import org.hubbers.mcp.protocol.McpPromptInfo.McpPromptArgument;
import org.hubbers.mcp.protocol.McpPromptResult;
import org.hubbers.mcp.protocol.McpPromptResult.McpPromptMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exposes Hubbers agent manifests and skill manifests as MCP prompts.
 * Agent system prompts and skill instructions become available as reusable
 * prompt templates in external chat UIs.
 *
 * <p>Skills are exposed as prompts (not tools) because their {@code llm-prompt} mode
 * content is a methodology/instruction set best injected into the client model's
 * context rather than executed as a separate LLM call.
 */
@Slf4j
public class McpPromptProvider {

    private static final String PREFIX_SKILL = "skill.";

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
     * Lists all available agent and skill prompts.
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

        for (String name : artifactRepository.listSkills()) {
            try {
                SkillMetadata metadata = artifactRepository.getSkillMetadata(name);
                if (metadata != null) {
                    convertSkill(metadata).ifPresent(prompts::add);
                }
            } catch (Exception e) {
                log.warn("Failed to convert skill '{}' to MCP prompt: {}", name, e.getMessage());
            }
        }

        log.info("MCP prompts/list: {} prompts available", prompts.size());
        return prompts;
    }

    /**
     * Gets a specific prompt by name with optional argument substitution.
     * Supports both agent names and skill names (prefixed with "skill.").
     *
     * @param name    the prompt name (agent name or "skill.&lt;skill-name&gt;")
     * @param request optional request text to inject as user message
     * @return the prompt result, or empty if not found
     */
    public Optional<McpPromptResult> getPrompt(String name, String request) {
        if (name.startsWith(PREFIX_SKILL)) {
            return getSkillPrompt(name.substring(PREFIX_SKILL.length()), request);
        }
        try {
            AgentManifest manifest = artifactRepository.loadAgent(name);
            return Optional.of(buildAgentPromptResult(manifest, request));
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

    private McpPromptResult buildAgentPromptResult(AgentManifest manifest, String request) {
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

    private Optional<McpPromptInfo> convertSkill(SkillMetadata metadata) {
        if (metadata.getName() == null) {
            return Optional.empty();
        }

        String promptName = PREFIX_SKILL + metadata.getName();
        String description = metadata.getDescription();
        if (description == null || description.isBlank()) {
            description = "Skill prompt: " + metadata.getName();
        }

        List<McpPromptArgument> arguments = new ArrayList<>();
        arguments.add(McpPromptArgument.builder()
                .name("request")
                .description("The task or input for this skill")
                .required(true)
                .build());

        return Optional.of(McpPromptInfo.builder()
                .name(promptName)
                .description(description)
                .arguments(arguments)
                .build());
    }

    /**
     * Gets a skill prompt by loading the full SKILL.md body as instructions.
     *
     * @param skillName the skill name
     * @param request   optional request text to inject as user message
     * @return the prompt result, or empty if skill not found
     */
    private Optional<McpPromptResult> getSkillPrompt(String skillName, String request) {
        try {
            SkillManifest manifest = artifactRepository.loadSkill(skillName);
            return Optional.of(buildSkillPromptResult(manifest, request));
        } catch (Exception e) {
            log.warn("Failed to load skill prompt '{}': {}", skillName, e.getMessage());
            return Optional.empty();
        }
    }

    private McpPromptResult buildSkillPromptResult(SkillManifest manifest, String request) {
        List<McpPromptMessage> messages = new ArrayList<>();

        // Inject SKILL.md body as system-level instructions
        if (manifest.getBody() != null && !manifest.getBody().isBlank()) {
            messages.add(McpPromptMessage.builder()
                    .role("assistant")
                    .content(McpContent.text(manifest.getBody()))
                    .build());
        }

        // Add user request if provided
        if (request != null && !request.isBlank()) {
            messages.add(McpPromptMessage.builder()
                    .role("user")
                    .content(McpContent.text(request))
                    .build());
        }

        return McpPromptResult.builder()
                .description(manifest.getDescription())
                .messages(messages)
                .build();
    }
}
