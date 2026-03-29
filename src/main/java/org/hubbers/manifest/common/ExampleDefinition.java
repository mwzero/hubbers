package org.hubbers.manifest.common;

import com.fasterxml.jackson.databind.JsonNode;

public class ExampleDefinition {
    private String name;
    private String description;
    private JsonNode input;
    private JsonNode output;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonNode getInput() {
        return input;
    }

    public void setInput(JsonNode input) {
        this.input = input;
    }

    public JsonNode getOutput() {
        return output;
    }

    public void setOutput(JsonNode output) {
        this.output = output;
    }
}
