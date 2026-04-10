package org.hubbers.manifest.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the YAML frontmatter structure of a SKILL.md file
 * following the agentskills.io specification.
 */
@Data
public class SkillFrontmatter {
    /**
     * Required: 1-64 characters, lowercase alphanumeric and hyphens only.
     * Must not start/end with hyphen or contain consecutive hyphens.
     */
    private String name;

    /**
     * Required: 1-1024 characters describing what the skill does and when to use it.
     */
    private String description;

    /**
     * Optional: License name or reference to bundled license file.
     */
    private String license;

    /**
     * Optional: Max 500 characters indicating environment requirements.
     */
    private String compatibility;

    /**
     * Optional: Arbitrary key-value mapping for additional metadata.
     * Used for custom fields like execution_mode, author, version, etc.
     */
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Optional: Space-delimited list of pre-approved tools (experimental).
     */
    @JsonProperty("allowed-tools")
    private String allowedTools;

    /**
     * Helper method to get execution mode from metadata.
     * Defaults to "llm-prompt" if not specified.
     */
    public String getExecutionMode() {
        return metadata.getOrDefault("execution_mode", "llm-prompt");
    }

    /**
     * Helper method to set execution mode in metadata.
     */
    public void setExecutionMode(String mode) {
        metadata.put("execution_mode", mode);
    }
}
