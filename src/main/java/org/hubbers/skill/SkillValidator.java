package org.hubbers.skill;

import org.hubbers.manifest.skill.SkillFrontmatter;
import org.hubbers.manifest.skill.SkillManifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates skills against the agentskills.io specification.
 * 
 * Validation rules:
 * - name: 1-64 characters, lowercase alphanumeric and hyphens only
 * - name: must not start/end with hyphen or contain consecutive hyphens
 * - name: must match parent directory name
 * - description: 1-1024 characters, non-empty
 * - compatibility: max 500 characters (if provided)
 * - SKILL.md: must exist
 */
public class SkillValidator {
    private static final int NAME_MIN_LENGTH = 1;
    private static final int NAME_MAX_LENGTH = 64;
    private static final int DESCRIPTION_MIN_LENGTH = 1;
    private static final int DESCRIPTION_MAX_LENGTH = 1024;
    private static final int COMPATIBILITY_MAX_LENGTH = 500;

    // Pattern: lowercase letters, numbers, and hyphens only
    // No starting/ending hyphen, no consecutive hyphens
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^[a-z0-9]+(-[a-z0-9]+)*$"
    );

    /**
     * Validate a skill manifest against the agentskills.io specification.
     * 
     * @param manifest The skill manifest to validate
     * @return ValidationResult with success status and error messages
     */
    public ValidationResult validate(SkillManifest manifest) {
        List<String> errors = new ArrayList<>();

        // Validate frontmatter exists
        if (manifest.getFrontmatter() == null) {
            errors.add("Skill frontmatter is missing");
            return new ValidationResult(false, errors);
        }

        SkillFrontmatter frontmatter = manifest.getFrontmatter();

        // Validate name
        validateName(frontmatter.getName(), errors);

        // Validate description
        validateDescription(frontmatter.getDescription(), errors);

        // Validate name matches directory
        if (manifest.getSkillPath() != null) {
            validateNameMatchesDirectory(
                frontmatter.getName(), 
                manifest.getSkillPath(), 
                errors
            );
        }

        // Validate SKILL.md exists
        if (manifest.getSkillPath() != null) {
            validateSkillMdExists(manifest.getSkillPath(), errors);
        }

        // Validate optional fields
        validateOptionalFields(frontmatter, errors);

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate skill name according to spec.
     */
    private void validateName(String name, List<String> errors) {
        if (name == null || name.isEmpty()) {
            errors.add("Skill name is required");
            return;
        }

        if (name.length() < NAME_MIN_LENGTH || name.length() > NAME_MAX_LENGTH) {
            errors.add(String.format(
                "Skill name must be %d-%d characters (got %d)", 
                NAME_MIN_LENGTH, NAME_MAX_LENGTH, name.length()
            ));
        }

        if (!NAME_PATTERN.matcher(name).matches()) {
            errors.add(
                "Skill name must contain only lowercase letters, numbers, and hyphens. " +
                "Cannot start/end with hyphen or contain consecutive hyphens. " +
                "Got: " + name
            );
        }
    }

    /**
     * Validate skill description according to spec.
     */
    private void validateDescription(String description, List<String> errors) {
        if (description == null || description.isEmpty()) {
            errors.add("Skill description is required");
            return;
        }

        if (description.length() < DESCRIPTION_MIN_LENGTH || 
            description.length() > DESCRIPTION_MAX_LENGTH) {
            errors.add(String.format(
                "Skill description must be %d-%d characters (got %d)",
                DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH, description.length()
            ));
        }
    }

    /**
     * Validate that skill name matches the directory name.
     */
    private void validateNameMatchesDirectory(String name, Path skillPath, List<String> errors) {
        String directoryName = skillPath.getFileName().toString();
        if (!name.equals(directoryName)) {
            errors.add(String.format(
                "Skill name '%s' must match directory name '%s'",
                name, directoryName
            ));
        }
    }

    /**
     * Validate that SKILL.md file exists.
     */
    private void validateSkillMdExists(Path skillPath, List<String> errors) {
        Path skillMdPath = skillPath.resolve("SKILL.md");
        if (!Files.exists(skillMdPath)) {
            errors.add("SKILL.md file not found in: " + skillPath);
        }
    }

    /**
     * Validate optional fields according to spec.
     */
    private void validateOptionalFields(SkillFrontmatter frontmatter, List<String> errors) {
        // Validate compatibility length if provided
        String compatibility = frontmatter.getCompatibility();
        if (compatibility != null && compatibility.length() > COMPATIBILITY_MAX_LENGTH) {
            errors.add(String.format(
                "Compatibility field must be max %d characters (got %d)",
                COMPATIBILITY_MAX_LENGTH, compatibility.length()
            ));
        }

        // Validate execution_mode if provided
        String executionMode = frontmatter.getExecutionMode();
        if (executionMode != null && 
            !executionMode.equals("llm-prompt") && 
            !executionMode.equals("script") && 
            !executionMode.equals("hybrid")) {
            errors.add(String.format(
                "Invalid execution_mode: '%s'. Must be 'llm-prompt', 'script', or 'hybrid'",
                executionMode
            ));
        }
    }

    /**
     * Validation result containing success status and error messages.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("\n", errors);
        }

        @Override
        public String toString() {
            if (valid) {
                return "Validation passed";
            } else {
                return "Validation failed:\n" + getErrorMessage();
            }
        }
    }
}
