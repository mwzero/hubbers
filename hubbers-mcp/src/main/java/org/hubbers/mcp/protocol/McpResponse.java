package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 response envelope for MCP protocol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {

    /** Must be "2.0" per JSON-RPC spec. */
    private String jsonrpc;

    /** Request identifier echoed back for correlation. */
    private Object id;

    /** Successful result payload. Mutually exclusive with {@link #error}. */
    private Object result;

    /** Error payload on failure. Mutually exclusive with {@link #result}. */
    private McpError error;

    /**
     * Creates a success response.
     *
     * @param id     the request ID to echo back
     * @param result the result payload
     * @return a JSON-RPC 2.0 success response
     */
    public static McpResponse success(Object id, Object result) {
        return McpResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(result)
                .build();
    }

    /**
     * Creates an error response.
     *
     * @param id      the request ID to echo back
     * @param code    JSON-RPC error code
     * @param message human-readable error message
     * @return a JSON-RPC 2.0 error response
     */
    public static McpResponse error(Object id, int code, String message) {
        return McpResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(new McpError(code, message, null))
                .build();
    }

    /** Standard JSON-RPC error: method not found (-32601). */
    public static McpResponse methodNotFound(Object id, String method) {
        return error(id, -32601, "Method not found: " + method);
    }

    /** Standard JSON-RPC error: invalid params (-32602). */
    public static McpResponse invalidParams(Object id, String detail) {
        return error(id, -32602, "Invalid params: " + detail);
    }

    /** Standard JSON-RPC error: internal error (-32603). */
    public static McpResponse internalError(Object id, String detail) {
        return error(id, -32603, "Internal error: " + detail);
    }
}
