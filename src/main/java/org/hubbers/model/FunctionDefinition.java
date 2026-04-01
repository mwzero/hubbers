package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Function definition sent to LLM describing an available tool/task.
 * Generated from a ToolManifest to expose tasks to the LLM.
 */
public class FunctionDefinition {
    private final String name;
    private final String description;
    private final JsonNode parameters;  // JSON Schema for function parameters
    private final List<ExampleDefinition> examples;  // Usage examples to guide LLM

    public FunctionDefinition(String name, String description, JsonNode parameters) {
        this(name, description, parameters, new ArrayList<>());
    }

    public FunctionDefinition(String name, String description, JsonNode parameters, List<ExampleDefinition> examples) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.examples = examples != null ? examples : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public List<ExampleDefinition> getExamples() {
        return examples;
    }

    @Override
    public String toString() {
        return "FunctionDefinition{name='" + name + "', description='" + description + "', examples=" + examples.size() + "}";
    }
}
