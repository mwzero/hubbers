package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.FunctionDefinition;

/**
 * Unified converter for all artifact types (tools, agents, pipelines) to LLM FunctionDefinitions.
 * This allows the universal task agent to call any artifact type as if it were a tool.
 */
public class ArtifactToFunctionConverter {
    private final ObjectMapper mapper;
    private final ToolToFunctionConverter toolConverter;

    public ArtifactToFunctionConverter(ObjectMapper mapper) {
        this.mapper = mapper;
        this.toolConverter = new ToolToFunctionConverter(mapper);
    }

    /**
     * Convert a tool manifest to a function definition.
     * Delegates to the existing ToolToFunctionConverter.
     */
    public FunctionDefinition convertTool(ToolManifest toolManifest) {
        return toolConverter.convert(toolManifest);
    }

    /**
     * Convert an agent manifest to a function definition.
     * Agents are AI-powered tasks that use LLM reasoning (sentiment, NER, summarization, etc.).
     */
    public FunctionDefinition convertAgent(AgentManifest agentManifest) {
        String name = extractAgentName(agentManifest);
        String description = extractAgentDescription(agentManifest);
        JsonNode parameters = extractAgentParameters(agentManifest);
        var examples = agentManifest.getExamples();

        return new FunctionDefinition(name, description, parameters, examples);
    }

    /**
     * Convert a pipeline manifest to a function definition.
     * Pipelines are pre-built multi-step workflows.
     */
    public FunctionDefinition convertPipeline(PipelineManifest pipelineManifest) {
        String name = extractPipelineName(pipelineManifest);
        String description = extractPipelineDescription(pipelineManifest);
        JsonNode parameters = extractPipelineParameters(pipelineManifest);
        var examples = pipelineManifest.getExamples();

        return new FunctionDefinition(name, description, parameters, examples);
    }

    // Agent extraction methods
    private String extractAgentName(AgentManifest manifest) {
        if (manifest.getAgent() != null && manifest.getAgent().getName() != null) {
            return manifest.getAgent().getName();
        }
        return "unknown_agent";
    }

    private String extractAgentDescription(AgentManifest manifest) {
        StringBuilder desc = new StringBuilder();
        
        if (manifest.getAgent() != null && manifest.getAgent().getDescription() != null) {
            desc.append(manifest.getAgent().getDescription());
        } else {
            desc.append("Execute agent: ").append(extractAgentName(manifest));
        }
        
        // Add context about it being an agent
        desc.append(" [AGENT - AI-powered task");
        if (manifest.getTools() != null && !manifest.getTools().isEmpty()) {
            desc.append(" with access to ").append(manifest.getTools().size()).append(" tools");
        }
        desc.append("]");
        
        return desc.toString();
    }

    private JsonNode extractAgentParameters(AgentManifest manifest) {
        if (manifest.getInput() != null && manifest.getInput().getSchema() != null) {
            try {
                return mapper.valueToTree(manifest.getInput().getSchema());
            } catch (Exception e) {
                // Fall through to empty schema
            }
        }

        // Return empty object schema
        ObjectNode emptySchema = mapper.createObjectNode();
        emptySchema.put("type", "object");
        emptySchema.set("properties", mapper.createObjectNode());
        return emptySchema;
    }

    // Pipeline extraction methods
    private String extractPipelineName(PipelineManifest manifest) {
        if (manifest.getPipeline() != null && manifest.getPipeline().getName() != null) {
            return manifest.getPipeline().getName();
        }
        return "unknown_pipeline";
    }

    private String extractPipelineDescription(PipelineManifest manifest) {
        StringBuilder desc = new StringBuilder();
        
        if (manifest.getPipeline() != null && manifest.getPipeline().getDescription() != null) {
            desc.append(manifest.getPipeline().getDescription());
        } else {
            desc.append("Execute pipeline: ").append(extractPipelineName(manifest));
        }
        
        // Add context about it being a pipeline
        desc.append(" [PIPELINE - Complete workflow");
        if (manifest.getSteps() != null && !manifest.getSteps().isEmpty()) {
            desc.append(" with ").append(manifest.getSteps().size()).append(" steps");
        }
        desc.append("]");
        
        return desc.toString();
    }

    private JsonNode extractPipelineParameters(PipelineManifest manifest) {
        // First try explicit input definition (if added to PipelineManifest)
        if (manifest.getInput() != null && manifest.getInput().getSchema() != null) {
            try {
                return mapper.valueToTree(manifest.getInput().getSchema());
            } catch (Exception e) {
                // Fall through to derivation from examples
            }
        }

        // Try to derive from examples
        if (manifest.getExamples() != null && !manifest.getExamples().isEmpty()) {
            var firstExample = manifest.getExamples().get(0);
            if (firstExample.getInput() != null) {
                try {
                    // Create a schema from the example input structure
                    JsonNode exampleInput = mapper.valueToTree(firstExample.getInput());
                    return deriveSchemaFromExample(exampleInput);
                } catch (Exception e) {
                    // Fall through to empty schema
                }
            }
        }

        // Return empty object schema (pipeline accepts any input)
        ObjectNode emptySchema = mapper.createObjectNode();
        emptySchema.put("type", "object");
        emptySchema.set("properties", mapper.createObjectNode());
        return emptySchema;
    }

    /**
     * Derive a basic JSON schema from an example input.
     * This is a simple heuristic that creates required properties for all fields in the example.
     */
    private JsonNode deriveSchemaFromExample(JsonNode example) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        if (example.isObject()) {
            example.fields().forEachRemaining(entry -> {
                ObjectNode propSchema = mapper.createObjectNode();
                JsonNode value = entry.getValue();
                
                if (value.isTextual()) {
                    propSchema.put("type", "string");
                } else if (value.isNumber()) {
                    propSchema.put("type", "number");
                } else if (value.isBoolean()) {
                    propSchema.put("type", "boolean");
                } else if (value.isArray()) {
                    propSchema.put("type", "array");
                } else if (value.isObject()) {
                    propSchema.put("type", "object");
                }
                
                properties.set(entry.getKey(), propSchema);
            });
        }
        
        schema.set("properties", properties);
        return schema;
    }
}
