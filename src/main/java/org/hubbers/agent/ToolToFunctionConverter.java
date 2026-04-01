package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.FunctionDefinition;

/**
 * Utility to convert Hubbers ToolManifest (task definition) 
 * to FunctionDefinition (LLM function calling format).
 */
public class ToolToFunctionConverter {
    private final ObjectMapper mapper;

    public ToolToFunctionConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Convert a ToolManifest to a FunctionDefinition that can be sent to LLM.
     * Includes examples to help LLM understand how to use the tool.
     */
    public FunctionDefinition convert(ToolManifest toolManifest) {
        String name = extractToolName(toolManifest);
        String description = extractDescription(toolManifest);
        JsonNode parameters = extractParameters(toolManifest);
        var examples = toolManifest.getExamples();

        return new FunctionDefinition(name, description, parameters, examples);
    }

    private String extractToolName(ToolManifest manifest) {
        String name = manifest.getTool().getName();
        return name != null ? name : "unknown";
    }

    private String extractDescription(ToolManifest manifest) {
        String description = manifest.getTool().getDescription();
        if (description != null && !description.isEmpty()) {
            return description;
        }
        return "Execute " + extractToolName(manifest) + " tool";
    }

    private JsonNode extractParameters(ToolManifest manifest) {
        if (manifest.getInput() == null || manifest.getInput().getSchema() == null) {
            // Return empty object schema
            ObjectNode emptySchema = mapper.createObjectNode();
            emptySchema.put("type", "object");
            emptySchema.set("properties", mapper.createObjectNode());
            return emptySchema;
        }

        // Convert input schema to JSON
        try {
            return mapper.valueToTree(manifest.getInput().getSchema());
        } catch (Exception e) {
            ObjectNode errorSchema = mapper.createObjectNode();
            errorSchema.put("type", "object");
            return errorSchema;
        }
    }
}
