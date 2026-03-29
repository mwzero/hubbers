package org.hubbers.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.model.ModelRequest;
import org.hubbers.util.JacksonFactory;

public class AgentPromptBuilder {
    private final ObjectMapper mapper = JacksonFactory.jsonMapper();

    public ModelRequest build(AgentRunContext context) {
        AgentManifest manifest = context.getManifest();
        ModelRequest request = new ModelRequest();
        request.setSystemPrompt(buildSystemPrompt(manifest));
        request.setModel(manifest.getModel().getName());
        request.setTemperature(manifest.getModel().getTemperature());
        try {
            request.setUserPrompt(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(context.getInput()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize input", e);
        }
        return request;
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
