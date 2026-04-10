package org.hubbers.manifest.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight metadata for progressive disclosure.
 * Loaded at startup for all skills - contains only essential info for discovery.
 * Full SKILL.md is loaded only when skill is activated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetadata {
    /**
     * Skill name (matches directory name and frontmatter name).
     */
    private String name;

    /**
     * Brief description of what the skill does and when to use it.
     */
    private String description;

    /**
     * Execution mode: llm-prompt, script, or hybrid.
     * Defaults to llm-prompt if not specified.
     */
    private String executionMode;

    /**
     * Path to the skill directory (for lazy loading full content).
     */
    private String skillPath;
}
