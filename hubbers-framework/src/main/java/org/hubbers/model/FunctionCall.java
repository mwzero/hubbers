package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a function call request from the LLM to execute a tool/task.
 * This is returned by models that support function calling.
 */
public class FunctionCall {
    private final String id;
    private final String name;  // References a ToolManifest name
    private final JsonNode arguments;

    public FunctionCall(String id, String name, JsonNode arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JsonNode getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "FunctionCall{id='" + id + "', name='" + name + "', arguments=" + arguments + "}";
    }
}
