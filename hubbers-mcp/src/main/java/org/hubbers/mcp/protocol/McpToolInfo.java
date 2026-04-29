package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP tool information returned by {@code tools/list}.
 * Maps Hubbers artifacts (tools, pipelines, agents) to MCP tool definitions.
 * Skills are exposed as MCP prompts instead.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools Spec</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolInfo {

    /** Unique tool name. Uses convention: tool.*, pipeline.*, agent.* */
    private String name;

    /** Human-readable description of what the tool does. */
    private String description;

    /** JSON Schema for the tool's input parameters. */
    private JsonNode inputSchema;
}
