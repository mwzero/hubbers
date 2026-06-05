package org.hubbers.react.tool;

import java.util.List;

/**
 * A single entry in the tool catalog, parsed from a {@code tools.md} table row.
 *
 * <p>Used by {@link ToolIndex} to perform keyword-based search and by
 * {@link org.hubbers.react.ArtifactCatalogBuilder} to render a reduced subset
 * of the catalog into the system prompt.</p>
 *
 * @param name        the tool name as it appears in the {@code <call:name>} syntax
 * @param description human-readable description of what the tool does
 * @param syntax      the exact call syntax to copy into the prompt
 * @param tags        extra keywords extracted from description for scoring (lowercased)
 */
public record ToolEntry(String name, String description, String syntax, List<String> tags) {

    /** Returns the full Markdown table row for this entry. */
    public String toMarkdownRow() {
        return "| " + description + " | `" + syntax + "` |";
    }
}
