package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP resource information returned by {@code resources/list}.
 * Represents artifact manifests, execution logs, and other repository content
 * accessible to MCP clients.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/resources/">MCP Resources Spec</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResourceInfo {

    /** Unique resource URI (e.g. "hubbers://agents/universal.task/manifest") */
    private String uri;

    /** Human-readable name. */
    private String name;

    /** Optional description. */
    private String description;

    /** MIME type (e.g. "text/yaml", "application/json"). */
    private String mimeType;
}
