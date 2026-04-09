package org.hubbers.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.skill.SkillFrontmatter;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.hubbers.util.JacksonFactory;
import org.hubbers.util.MarkdownSectionExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Parser for SKILL.md files in pure Markdown format with ## Metadata section containing JSON.
 * 
 * Format:
 * ## Metadata
 * ```json
 * {"name": "skill-name", "description": "...", "executionMode": "llm-prompt"}
 * ```
 * 
 * ## Instructions
 * Your skill instructions here...
 * 
 * Supports progressive disclosure for lightweight metadata extraction.
 */
public class SkillMdParser {
    private static final Logger log = LoggerFactory.getLogger(SkillMdParser.class);
    
    private final ObjectMapper jsonMapper;
    private final MarkdownSectionExtractor sectionExtractor;

    public SkillMdParser() {
        this.jsonMapper = JacksonFactory.jsonMapper();
        this.sectionExtractor = new MarkdownSectionExtractor(jsonMapper);
    }

    public SkillMdParser(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.sectionExtractor = new MarkdownSectionExtractor(jsonMapper);
    }

    /**
     * Parse a SKILL.md file into a SkillManifest.
     * 
     * @param skillPath Path to the skill directory
     * @return Parsed SkillManifest
     * @throws IOException if parsing fails
     */
    public SkillManifest parse(Path skillPath) throws IOException {
        Path skillMdPath = skillPath.resolve("SKILL.md");
        
        if (!Files.exists(skillMdPath)) {
            throw new IOException("SKILL.md not found in: " + skillPath);
        }

        String content = Files.readString(skillMdPath);
        
        log.debug("Parsing SKILL.md with Markdown sections format: {}", skillPath);
        return parseMarkdownFormat(content, skillPath);
    }
    
    /**
     * Parse Markdown sections format with JSON metadata.
     */
    private SkillManifest parseMarkdownFormat(String content, Path skillPath) throws IOException {
        // Extract ## Metadata section
        JsonNode metadataJson = sectionExtractor.extractSectionAsJson(content, "Metadata");
        if (metadataJson == null) {
            throw new IOException("Missing required ## Metadata section in SKILL.md: " + skillPath);
        }
        
        // Build SkillFrontmatter from metadata
        SkillFrontmatter frontmatter = new SkillFrontmatter();
        frontmatter.setName(getRequiredField(metadataJson, "name", skillPath).asText());
        frontmatter.setDescription(getRequiredField(metadataJson, "description", skillPath).asText());
        
        // Optional fields
        if (metadataJson.has("license")) {
            frontmatter.setLicense(metadataJson.get("license").asText());
        }
        if (metadataJson.has("compatibility")) {
            frontmatter.setCompatibility(metadataJson.get("compatibility").asText());
        }
        
        // Build metadata map
        Map<String, String> metadata = new LinkedHashMap<>();
        if (metadataJson.has("executionMode")) {
            metadata.put("execution_mode", metadataJson.get("executionMode").asText());
        }
        if (metadataJson.has("author")) {
            metadata.put("author", metadataJson.get("author").asText());
        }
        if (metadataJson.has("version")) {
            metadata.put("version", metadataJson.get("version").asText());
        }
        frontmatter.setMetadata(metadata);
        
        // Extract instructions body (everything after ## Metadata, typically starts with ## Instructions heading)
        String instructionsBody = sectionExtractor.extractSectionText(content, "Instructions");
        if (instructionsBody == null || instructionsBody.isBlank()) {
            throw new IOException("Missing required ## Instructions section in SKILL.md: " + skillPath);
        }
        
        // Create manifest
        SkillManifest manifest = new SkillManifest();
        manifest.setFrontmatter(frontmatter);
        manifest.setBody(instructionsBody.trim());
        manifest.setSkillPath(skillPath);

        // Scan optional directories
        scanOptionalDirectories(manifest, skillPath);
        
        log.debug("Parsed skill (Markdown format): {}", frontmatter.getName());
        return manifest;
    }

    /**
     * Parse only the lightweight metadata for progressive disclosure.
     * Only reads ## Metadata section without parsing the full body.
     * 
     * @param skillPath Path to the skill directory
     * @return Lightweight SkillMetadata
     * @throws IOException if parsing fails
     */
    public SkillMetadata parseMetadata(Path skillPath) throws IOException {
        Path skillMdPath = skillPath.resolve("SKILL.md");
        
        if (!Files.exists(skillMdPath)) {
            throw new IOException("SKILL.md not found in: " + skillPath);
        }

        String content = Files.readString(skillMdPath);
        return parseMetadataFromMarkdown(content, skillPath);
    }
    
    /**
     * Parse metadata from Markdown format (lightweight - only reads ## Metadata section).
     */
    private SkillMetadata parseMetadataFromMarkdown(String content, Path skillPath) throws IOException {
        // Only extract ## Metadata section for progressive disclosure
        JsonNode metadataJson = sectionExtractor.extractSectionAsJson(content, "Metadata");
        if (metadataJson == null) {
            throw new IOException("Missing required ## Metadata section in SKILL.md: " + skillPath);
        }
        
        String name = getRequiredField(metadataJson, "name", skillPath).asText();
        String description = getRequiredField(metadataJson, "description", skillPath).asText();
        String executionMode = metadataJson.has("executionMode") 
            ? metadataJson.get("executionMode").asText() 
            : "llm-prompt"; // default
        
        return new SkillMetadata(name, description, executionMode, skillPath.toString());
    }
    
    /**
     * Get required field from JSON node, throw clear error if missing.
     */
    private JsonNode getRequiredField(JsonNode node, String fieldName, Path skillPath) throws IOException {
        if (!node.has(fieldName)) {
            throw new IOException(String.format(
                "Missing required field '%s' in ## Metadata section: %s",
                fieldName, skillPath
            ));
        }
        return node.get(fieldName);
    }

    /**
     * Scan optional directories (scripts/, references/, assets/).
     */
    private void scanOptionalDirectories(SkillManifest manifest, Path skillPath) {
        manifest.setScripts(scanDirectory(skillPath.resolve("scripts")));
        manifest.setReferences(scanDirectory(skillPath.resolve("references")));
        manifest.setAssets(scanDirectory(skillPath.resolve("assets")));
    }

    /**
     * Scan a directory and return all file paths.
     */
    private List<Path> scanDirectory(Path directory) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return new ArrayList<>();
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                .filter(Files::isRegularFile)
                .toList();
        } catch (IOException e) {
            log.warn("Failed to scan directory: {}", directory, e);
            return new ArrayList<>();
        }
    }
}
