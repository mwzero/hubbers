package org.hubbers.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.mcp.protocol.McpToolInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the Hubbers ArtifactRepository to MCP tool definitions.
 * Converts all artifact types (tools, pipelines, agents, skills) into MCP ToolInfo objects
 * with type-prefixed names for routing during execution.
 *
 * <p>Naming convention:
 * <ul>
 *   <li>{@code tool.<name>} — e.g., {@code tool.web.search}</li>
 *   <li>{@code pipeline.<name>} — e.g., {@code pipeline.file.backup}</li>
 *   <li>{@code agent.<name>} — e.g., {@code agent.universal.task}</li>
 *   <li>{@code skill.<name>} — e.g., {@code skill.code.review}</li>
 * </ul>
 */
@Slf4j
public class McpToolProvider {

    private static final String PREFIX_TOOL = "tool.";
    private static final String PREFIX_PIPELINE = "pipeline.";
    private static final String PREFIX_AGENT = "agent.";
    private static final String PREFIX_SKILL = "skill.";

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper mapper;

    /**
     * Creates a new McpToolProvider.
     *
     * @param artifactRepository the artifact repository for discovering artifacts
     * @param mapper             Jackson mapper for JSON schema generation
     */
    public McpToolProvider(ArtifactRepository artifactRepository, ObjectMapper mapper) {
        this.artifactRepository = artifactRepository;
        this.mapper = mapper;
    }

    /**
     * Lists all available Hubbers artifacts as MCP tools.
     *
     * @return list of MCP tool definitions
     */
    public List<McpToolInfo> listTools() {
        List<McpToolInfo> tools = new ArrayList<>();

        for (String name : artifactRepository.listTools()) {
            try {
                ToolManifest manifest = artifactRepository.loadTool(name);
                tools.add(convertTool(manifest));
            } catch (Exception e) {
                log.warn("Failed to convert tool '{}' to MCP: {}", name, e.getMessage());
            }
        }

        for (String name : artifactRepository.listPipelines()) {
            try {
                PipelineManifest manifest = artifactRepository.loadPipeline(name);
                tools.add(convertPipeline(manifest));
            } catch (Exception e) {
                log.warn("Failed to convert pipeline '{}' to MCP: {}", name, e.getMessage());
            }
        }

        for (String name : artifactRepository.listAgents()) {
            try {
                AgentManifest manifest = artifactRepository.loadAgent(name);
                tools.add(convertAgent(manifest));
            } catch (Exception e) {
                log.warn("Failed to convert agent '{}' to MCP: {}", name, e.getMessage());
            }
        }

        for (String name : artifactRepository.listSkills()) {
            try {
                SkillMetadata metadata = artifactRepository.getSkillMetadata(name);
                tools.add(convertSkill(metadata));
            } catch (Exception e) {
                log.warn("Failed to convert skill '{}' to MCP: {}", name, e.getMessage());
            }
        }

        log.info("MCP tools/list: {} tools available", tools.size());
        return tools;
    }

    private McpToolInfo convertTool(ToolManifest manifest) {
        String name = PREFIX_TOOL + manifest.getTool().getName();
        String description = manifest.getTool().getDescription();
        if (description == null || description.isBlank()) {
            description = "Execute tool: " + manifest.getTool().getName();
        }
        return McpToolInfo.builder()
                .name(name)
                .description(description)
                .inputSchema(extractInputSchema(manifest))
                .build();
    }

    private McpToolInfo convertPipeline(PipelineManifest manifest) {
        String name = PREFIX_PIPELINE + manifest.getPipeline().getName();
        StringBuilder desc = new StringBuilder();
        if (manifest.getPipeline().getDescription() != null) {
            desc.append(manifest.getPipeline().getDescription());
        } else {
            desc.append("Execute pipeline: ").append(manifest.getPipeline().getName());
        }
        if (manifest.getSteps() != null && !manifest.getSteps().isEmpty()) {
            desc.append(" (").append(manifest.getSteps().size()).append(" steps)");
        }
        return McpToolInfo.builder()
                .name(name)
                .description(desc.toString())
                .inputSchema(extractPipelineInputSchema(manifest))
                .build();
    }

    private McpToolInfo convertAgent(AgentManifest manifest) {
        String agentName = manifest.getAgent() != null ? manifest.getAgent().getName() : "unknown";
        String name = PREFIX_AGENT + agentName;
        String description;
        if (manifest.getAgent() != null && manifest.getAgent().getDescription() != null) {
            description = manifest.getAgent().getDescription();
        } else {
            description = "Execute agent: " + agentName;
        }
        return McpToolInfo.builder()
                .name(name)
                .description(description)
                .inputSchema(extractAgentInputSchema(manifest))
                .build();
    }

    private McpToolInfo convertSkill(SkillMetadata metadata) {
        String name = PREFIX_SKILL + metadata.getName();
        String description = metadata.getDescription();
        if (description == null || description.isBlank()) {
            description = "Execute skill: " + metadata.getName();
        }
        return McpToolInfo.builder()
                .name(name)
                .description(description)
                .inputSchema(createSkillInputSchema())
                .build();
    }

    private JsonNode extractInputSchema(ToolManifest manifest) {
        if (manifest.getInput() != null && manifest.getInput().getSchema() != null) {
            try {
                return mapper.valueToTree(manifest.getInput().getSchema());
            } catch (Exception e) {
                log.debug("Failed to convert tool input schema: {}", e.getMessage());
            }
        }
        return createEmptyObjectSchema();
    }

    private JsonNode extractPipelineInputSchema(PipelineManifest manifest) {
        if (manifest.getInput() != null && manifest.getInput().getSchema() != null) {
            try {
                return mapper.valueToTree(manifest.getInput().getSchema());
            } catch (Exception e) {
                log.debug("Failed to convert pipeline input schema: {}", e.getMessage());
            }
        }
        return createEmptyObjectSchema();
    }

    private JsonNode extractAgentInputSchema(AgentManifest manifest) {
        if (manifest.getInput() != null && manifest.getInput().getSchema() != null) {
            try {
                return mapper.valueToTree(manifest.getInput().getSchema());
            } catch (Exception e) {
                log.debug("Failed to convert agent input schema: {}", e.getMessage());
            }
        }
        // Default: agents accept a "request" string
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode requestProp = mapper.createObjectNode();
        requestProp.put("type", "string");
        requestProp.put("description", "Natural language request for the agent");
        properties.set("request", requestProp);
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("request");
        schema.set("required", required);
        return schema;
    }

    private JsonNode createSkillInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ObjectNode inputProp = mapper.createObjectNode();
        inputProp.put("type", "string");
        inputProp.put("description", "Input for the skill");
        properties.set("input", inputProp);
        schema.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("input");
        schema.set("required", required);
        return schema;
    }

    private JsonNode createEmptyObjectSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        return schema;
    }
}
