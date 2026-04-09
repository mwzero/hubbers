package org.hubbers.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting Markdown sections and parsing JSON code blocks.
 * 
 * Supports parsing pure Markdown files with ## heading sections containing ```json blocks.
 * Used by AgentMdParser and SkillMdParser for YAML-free format.
 */
public class MarkdownSectionExtractor {
    private static final Logger log = LoggerFactory.getLogger(MarkdownSectionExtractor.class);
    
    // Matches ## Heading (case-insensitive, flexible whitespace)
    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "^##\\s+(%s)\\s*$\\n([\\s\\S]*?)(?=^##\\s+|\\z)",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    // Matches ```json ... ``` code blocks
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
        "```json\\s*\\n([\\s\\S]*?)\\n```",
        Pattern.CASE_INSENSITIVE
    );
    
    private final ObjectMapper jsonMapper;
    
    public MarkdownSectionExtractor(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }
    
    /**
     * Extract section content by heading name.
     * 
     * @param content Full Markdown content
     * @param heading Section heading (e.g., "Configuration", "Model")
     * @return Section content (everything after ## Heading until next ## or EOF), or null if not found
     */
    public String extractSection(String content, String heading) {
        if (content == null || heading == null) {
            return null;
        }
        
        // Build pattern with specific heading (handle both Unix \n and Windows \r\n line endings)
        Pattern pattern = Pattern.compile(
            "^##\\s+" + Pattern.quote(heading) + "\\s*$\\r?\\n([\\s\\S]*?)(?=^##\\s+|\\z)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String sectionContent = matcher.group(1).trim();
            log.debug("Extracted section '{}' ({} chars)", heading, sectionContent.length());
            return sectionContent;
        }
        
        log.debug("Section '{}' not found", heading);
        return null;
    }
    
    /**
     * Extract first JSON code block from text.
     * 
     * @param text Text containing ```json code block
     * @return Parsed JsonNode, or null if no code block found
     * @throws IOException if JSON parsing fails
     */
    public JsonNode extractJsonBlock(String text) throws IOException {
        if (text == null) {
            return null;
        }
        
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            String jsonText = matcher.group(1).trim();
            try {
                JsonNode result = jsonMapper.readTree(jsonText);
                log.debug("Parsed JSON block ({} bytes)", jsonText.length());
                return result;
            } catch (IOException e) {
                log.error("Failed to parse JSON block: {}", e.getMessage());
                throw new IOException("Invalid JSON in code block: " + e.getMessage(), e);
            }
        }
        
        log.debug("No JSON code block found in text");
        return null;
    }
    
    /**
     * Extract section and parse JSON code block within it (convenience method).
     * 
     * @param content Full Markdown content
     * @param heading Section heading
     * @return Parsed JsonNode from code block, or null if section or block not found
     * @throws IOException if JSON parsing fails
     */
    public JsonNode extractSectionAsJson(String content, String heading) throws IOException {
        String sectionContent = extractSection(content, heading);
        if (sectionContent == null) {
            return null;
        }
        return extractJsonBlock(sectionContent);
    }
    
    /**
     * Extract plain text from section (excluding code blocks).
     * Useful for ## Instructions sections that contain prose.
     * 
     * @param content Full Markdown content
     * @param heading Section heading
     * @return Plain text content with code blocks removed, or null if section not found
     */
    public String extractSectionText(String content, String heading) {
        String sectionContent = extractSection(content, heading);
        if (sectionContent == null) {
            return null;
        }
        
        // Remove all code blocks (```language ... ```)
        String textOnly = sectionContent.replaceAll("```[a-z]*\\s*\\n[\\s\\S]*?\\n```", "");
        return textOnly.trim();
    }
    
    /**
     * Check if content starts with YAML frontmatter delimiter.
     * Used for backward compatibility detection.
     * 
     * @param content File content
     * @return true if starts with "---" (YAML format)
     */
    public boolean hasYamlFrontmatter(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return content.trim().startsWith("---");
    }
}
