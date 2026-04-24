package org.hubbers.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP content item used in tool call results and prompt messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpContent {

    /** Content type: "text", "image", "resource". */
    private String type;

    /** Text content (when type is "text"). */
    private String text;

    /**
     * Creates a text content item.
     *
     * @param text the text content
     * @return a text content item
     */
    public static McpContent text(String text) {
        return McpContent.builder()
                .type("text")
                .text(text)
                .build();
    }
}
