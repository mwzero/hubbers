package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result returned by {@code tools/call} in MCP protocol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolCallResult {

    /** Content items returned by the tool execution. */
    private List<McpContent> content;

    /** Whether the tool call resulted in an error. */
    @JsonProperty("isError")
    private Boolean isError;

    /**
     * Creates a successful text result.
     *
     * @param text the result text (typically JSON-formatted)
     * @return a success result
     */
    public static McpToolCallResult success(String text) {
        return McpToolCallResult.builder()
                .content(List.of(McpContent.text(text)))
                .isError(false)
                .build();
    }

    /**
     * Creates an error result.
     *
     * @param errorMessage the error message
     * @return an error result
     */
    public static McpToolCallResult error(String errorMessage) {
        return McpToolCallResult.builder()
                .content(List.of(McpContent.text(errorMessage)))
                .isError(true)
                .build();
    }
}
