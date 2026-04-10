package org.hubbers.manifest.skill;

import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete skill manifest representing a parsed SKILL.md file
 * following the agentskills.io specification.
 * 
 * Directory structure:
 * skill-name/
 *   ├── SKILL.md          (required: metadata + instructions)
 *   ├── scripts/          (optional: executable code)
 *   ├── references/       (optional: documentation)
 *   └── assets/           (optional: templates, resources)
 */
@Data
public class SkillManifest {
    /**
     * YAML frontmatter from SKILL.md.
     */
    private SkillFrontmatter frontmatter;

    /**
     * Markdown body content (instructions) from SKILL.md.
     */
    private String body;

    /**
     * Optional model configuration from ## Model section.
     */
    private ModelConfig modelConfig;

    /**
     * Path to the skill directory.
     */
    private Path skillPath;

    /**
     * List of script files in the scripts/ directory.
     */
    private List<Path> scripts = new ArrayList<>();

    /**
     * List of reference files in the references/ directory.
     */
    private List<Path> references = new ArrayList<>();

    /**
     * List of asset files in the assets/ directory.
     */
    private List<Path> assets = new ArrayList<>();

    /**
     * Convenience method to get skill name from frontmatter.
     */
    public String getName() {
        return frontmatter != null ? frontmatter.getName() : null;
    }

    /**
     * Convenience method to get skill description from frontmatter.
     */
    public String getDescription() {
        return frontmatter != null ? frontmatter.getDescription() : null;
    }

    /**
     * Convenience method to get execution mode from frontmatter.
     */
    public String getExecutionMode() {
        return frontmatter != null ? frontmatter.getExecutionMode() : "llm-prompt";
    }

    /**
     * Check if skill has model configuration.
     */
    public boolean hasModelConfig() {
        return modelConfig != null;
    }

    /**
     * Inner class for model configuration.
     */
    @Data
    public static class ModelConfig {
        private String provider;
        private String name;
        private Double temperature;
    }

    /**
     * Check if skill has scripts directory.
     */
    public boolean hasScripts() {
        return scripts != null && !scripts.isEmpty();
    }

    /**
     * Check if skill has references directory.
     */
    public boolean hasReferences() {
        return references != null && !references.isEmpty();
    }

    /**
     * Check if skill has assets directory.
     */
    public boolean hasAssets() {
        return assets != null && !assets.isEmpty();
    }
}
