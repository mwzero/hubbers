package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 request envelope for MCP protocol.
 *
 * @see <a href="https://spec.modelcontextprotocol.io">MCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpRequest {

    /** Must be "2.0" per JSON-RPC spec. */
    private String jsonrpc;

    /** Request identifier for correlating responses. May be string or number. */
    private Object id;

    /** The MCP method to invoke (e.g., "initialize", "tools/list", "tools/call"). */
    private String method;

    /** Method-specific parameters. */
    private JsonNode params;
}
