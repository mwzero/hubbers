package org.hubbers.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.agent.Instructions;
import org.hubbers.model.ModelRequest;
import org.hubbers.util.JacksonFactory;

import java.util.Iterator;
import java.util.Map;

public class AgentPromptBuilder {
    private final ObjectMapper mapper = JacksonFactory.jsonMapper();

    public ModelRequest build(AgentRunContext context) {

        AgentManifest manifest = context.getManifest();
        Instructions instructions = manifest.getInstructions();
        JsonNode input = context.getInput();

        ModelRequest request = new ModelRequest();
        request.setSystemPrompt(buildSystemPrompt(manifest));
        request.setModel(manifest.getModel().getName());
        request.setTemperature(manifest.getModel().getTemperature());
        request.setThink(manifest.getModel().getThink());

        if (instructions.getUserPrompt() != null) {
            request.setUserPrompt(substituteFields(instructions.getUserPrompt(), input));
        } else {
            try {
                request.setUserPrompt(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(input));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot serialize input", e);
            }
        }
        return request;
    }

    /**
     * Returns the formatted user content by applying the {@code user_prompt} template
     * substitution from the manifest instructions. Falls back to the raw JSON input when no
     * template is defined.
     *
     * @param manifest agent manifest
     * @param input    input JSON node
     * @return formatted user content string ready to be used as a chat message
     */
    public String buildUserContent(AgentManifest manifest, JsonNode input) {
        Instructions instructions = manifest.getInstructions();
        if (instructions != null && instructions.getUserPrompt() != null) {
            return substituteFields(instructions.getUserPrompt(), input);
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize input", e);
        }
    }

    /**
     * Replaces {@code {fieldName}} placeholders in the template with the corresponding
     * top-level field value from the input JSON node.
     */
    private String substituteFields(String template, JsonNode input) {
        if (input == null || !input.isObject()) {
            return template;
        }
        String result = template;
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String buildSystemPrompt(AgentManifest manifest) {
        String basePrompt = manifest.getInstructions().getSystemPrompt();
        if (manifest.getOutput() == null || manifest.getOutput().getSchema() == null) {
            return basePrompt;
        }
        try {
            String schema = mapper.writeValueAsString(manifest.getOutput().getSchema());
            return basePrompt + "\nReturn ONLY valid JSON that matches this output schema: " + schema;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize output schema", e);
        }
    }
}
