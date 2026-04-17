package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;

/**
 * UserInputToolDriver - Requests input from the user during agent execution.
 * This is a special tool that signals the agent needs user clarification.
 * The agent will receive a prompt to ask the user, and the conversation continues naturally.
 */
public class UserInputToolDriver implements ToolDriver {
    private final ObjectMapper mapper;

    public UserInputToolDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "user-interaction";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        // Extract the prompt and field information  
        String prompt = input.has("prompt") ? input.get("prompt").asText() : "Please provide more information";
        String fieldName = input.has("field_name") ? input.get("field_name").asText() : "user_response";
        String fieldType = input.has("field_type") ? input.get("field_type").asText() : "text";
        String placeholder = input.has("placeholder") ? input.get("placeholder").asText() : "";
        
        // Create a special response that tells the agent to ask the user
        ObjectNode result = mapper.createObjectNode();
        result.put("status", "requires_user_input");
        result.put("prompt", prompt);
        result.put("field_name", fieldName);
        result.put("field_type", fieldType);
        result.put("placeholder", placeholder);
        result.put("message", "⏸️ AGENT NEEDS INPUT: The agent requires user input to continue. " +
                "Please ask the user: \"" + prompt + "\" and wait for their response.");
        
        // Instructions for the agent on how to proceed
        result.put("next_steps", "You MUST stop execution and return a message asking the user: \"" + prompt + "\". " +
                "When the user responds in the next message, extract their answer and use it.");
        
        return result;
    }
}
