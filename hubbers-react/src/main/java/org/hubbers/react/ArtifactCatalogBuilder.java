package org.hubbers.react;

import org.hubbers.react.model.FunctionDefinition;
import org.hubbers.react.tool.ToolEntry;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Markdown artifacts catalog injected into the system prompt during Phase 1.
 *
 * <p>Artifacts are grouped into three sections — Tools, Agents, and Skills — and each
 * entry is formatted as a short bullet with a truncated description.</p>
 */
@Slf4j
public class ArtifactCatalogBuilder {

    private final Path toolsPath;
    
    public ArtifactCatalogBuilder(Path toolPath) {
        this.toolsPath = toolPath;
    }
    public String build() {
        return loadResource(toolsPath, "Nessun dato disponibile.");
    }

    /**
     * Builds a Markdown catalog from the given function definitions.
     *
     * @param defs available function definitions
     * @return formatted Markdown string; never {@code null}
     */
    public String build(List<FunctionDefinition> defs) {
        if (defs == null || defs.isEmpty()) {
            return "No artifacts available.";
        }

        List<FunctionDefinition> tools = new ArrayList<>();
        List<FunctionDefinition> agents = new ArrayList<>();
        List<FunctionDefinition> skills = new ArrayList<>();

        for (FunctionDefinition def : defs) {
            String desc = def.getDescription() != null ? def.getDescription() : "";
            if (desc.contains("[AGENT")) {
                agents.add(def);
            } else if (desc.contains("[SKILL")) {
                skills.add(def);
            } else {
                tools.add(def);
            }
        }

        StringBuilder catalog = new StringBuilder("## Available Artifacts\n\n");
        catalog.append("**IMPORTANT**: Prefer tools that combine multiple steps into a single call "
                + "over calling individual tools separately.\n\n");

        appendSection(catalog, "Tools", tools);
        appendSection(catalog, "Agents", agents);
        appendSection(catalog, "Skills", skills);

        return catalog.toString().trim();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a reduced Markdown table from a pre-selected subset of {@link ToolEntry} items.
     * Preserves the exact header and syntax-enforcement note from the full {@code tools.md},
     * but includes only the provided entries.
     *
     * @param entries the tool subset selected by the RAG discovery step
     * @return Markdown string ready for injection into the system prompt
     */
    public String buildSubset(List<ToolEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return build(); // fallback to full catalog
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**IMPORTANTE**: usa ESATTAMENTE il testo nella colonna \"Sintassi Esatta\". Non cambiare il nome del tag.\n\n");
        sb.append("| Descrizione | Sintassi Esatta (copia letteralmente) |\n");
        sb.append("|-------------|---------------------------------------|\n");
        for (ToolEntry e : entries) {
            sb.append("| ").append(e.description()).append(" | `").append(e.syntax()).append("` |\n");
        }
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String title, List<FunctionDefinition> defs) {
        if (defs.isEmpty()) return;
        sb.append("**").append(title).append("** (").append(defs.size()).append("):\n");
        for (FunctionDefinition def : defs) {
            sb.append("- `").append(def.getName()).append("`: ")
                    .append(truncateDescription(def.getDescription())).append("\n");
        }
        sb.append("\n");
    }

    private String truncateDescription(String desc) {
        if (desc == null || desc.isEmpty()) return "(no description)";
        String clean = desc.replaceAll("\\s*\\[(?:AGENT|SKILL)[^]]*\\]", "").trim();
        return clean.length() > 120 ? clean.substring(0, 117) + "..." : clean;
    }

    private String loadResource(Path path, String fallback) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            log.warn("Impossibile leggere {}. Usando fallback.", path);
            return fallback;
        }
    }
}
