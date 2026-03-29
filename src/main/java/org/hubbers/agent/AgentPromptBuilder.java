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
        request.setSystemPrompt(manifest.getInstructions().getSystemPrompt());
        request.setModel(manifest.getModel().getName());
        request.setTemperature(manifest.getModel().getTemperature());
        try {
            request.setUserPrompt(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(context.getInput()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize input", e);
        }
        return request;
    }
}
