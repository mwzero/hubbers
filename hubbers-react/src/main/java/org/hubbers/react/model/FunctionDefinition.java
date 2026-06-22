package org.hubbers.react.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Function definition sent to the LLM describing an available tool or artifact.
 * Generated from a ToolManifest (or similar) to expose tasks to the LLM.
 */
@Getter
@Builder
@ToString
public class FunctionDefinition {

    private final String name;
    private final String description;
    private final JsonNode parameters; // JSON Schema for function parameters

    @Builder.Default
    private final List<ExampleDefinition> examples = new ArrayList<>(); // usage examples to guide LLM
}
