package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Function definition sent to LLM describing an available tool/task.
 * Generated from a ToolManifest to expose tasks to the LLM.
 */
@Getter
@Builder
@ToString
public class FunctionDefinition {
    private final String name;
    private final String description;
    private final JsonNode parameters;  // JSON Schema for function parameters
    
    @Builder.Default
    private final List<ExampleDefinition> examples = new ArrayList<>();  // Usage examples to guide LLM
}
