package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result returned by {@code prompts/get} in MCP protocol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpPromptResult {

    /** Optional description of the prompt. */
    private String description;

    /** The prompt messages. */
    private List<McpPromptMessage> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpPromptMessage {

        /** Message role: "user" or "assistant". */
        private String role;

        /** Message content. */
        private McpContent content;
    }
}
