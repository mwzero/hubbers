package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP prompt information returned by {@code prompts/list}.
 * Maps Hubbers agent manifests to MCP prompt templates.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/prompts/">MCP Prompts Spec</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpPromptInfo {

    /** Unique prompt name. */
    private String name;

    /** Human-readable description of the prompt. */
    private String description;

    /** Arguments the prompt accepts. */
    private List<McpPromptArgument> arguments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpPromptArgument {
        private String name;
        private String description;
        private Boolean required;
    }
}
