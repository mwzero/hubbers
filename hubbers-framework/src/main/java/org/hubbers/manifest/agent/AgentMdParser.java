package org.hubbers.manifest.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.common.ExampleDefinition;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.common.SchemaDefinition;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for AGENT.md files in pure Markdown format with ## sections containing JSON blocks.
 * 
 * Format:
 * ## Configuration
 * ```json
 * {"name": "agent-name", "version": "1.0.0", ...}
 * ```
 * 
 * ## Model
 * ```json
 * {"provider": "ollama", "name": "model", "temperature": 0.1}
 * ```
 * 
 * ## Instructions
 * Your agent instructions here...
 */
public class AgentMdParser {
    private static final Logger log = LoggerFactory.getLogger(AgentMdParser.class);
    
    private final ObjectMapper jsonMapper;
    private final MarkdownSectionExtractor sectionExtractor;

    public AgentMdParser() {
        this.jsonMapper = JacksonFactory.jsonMapper();
        this.sectionExtractor = new MarkdownSectionExtractor(jsonMapper);
    }

    public AgentMdParser(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.sectionExtractor = new MarkdownSectionExtractor(jsonMapper);
    }

    /**
     * Parse an AGENT.md file into an AgentManifest.
     * 
     * @param agentPath Path to the agent directory
     * @return Parsed AgentManifest
     * @throws IOException if parsing fails
     */
    public AgentManifest parse(Path agentPath) throws IOException {
        Path agentMdPath = agentPath.resolve("AGENT.md");
        
        if (!Files.exists(agentMdPath)) {
            throw new IOException("AGENT.md not found in: " + agentPath);
        }

        String content = Files.readString(agentMdPath);
        
        log.debug("Parsing AGENT.md with Markdown sections format: {}", agentPath);
        return parseMarkdownFormat(content, agentPath);
    }
    
    /**
     * Parse Markdown sections format with JSON blocks.
     */
    private AgentManifest parseMarkdownFormat(String content, Path agentPath) throws IOException {
        AgentManifest manifest = new AgentManifest();
        
        // Extract ## Configuration section
        JsonNode configJson = sectionExtractor.extractSectionAsJson(content, "Configuration");
        if (configJson == null) {
            throw new IOException("Missing required ## Configuration section in AGENT.md: " + agentPath);
        }
        
        // Parse metadata
        Metadata metadata = new Metadata();
        metadata.setName(getRequiredField(configJson, "name", agentPath).asText());
        metadata.setVersion(getRequiredField(configJson, "version", agentPath).asText());
        metadata.setDescription(getRequiredField(configJson, "description", agentPath).asText());
        manifest.setAgent(metadata);
        
        // Parse config fields (tools, maxIterations, timeoutSeconds)
        Map<String, Object> config = new LinkedHashMap<>();
        if (configJson.has("tools")) {
            List<String> tools = new ArrayList<>();
            configJson.get("tools").forEach(node -> tools.add(node.asText()));
            config.put("tools", tools);
            manifest.setTools(tools);
        }
        if (configJson.has("maxIterations")) {
            config.put("max_iterations", configJson.get("maxIterations").asInt());
        }
        if (configJson.has("timeoutSeconds")) {
            config.put("timeout_seconds", configJson.get("timeoutSeconds").asInt());
        }
        manifest.setConfig(config);
        
        // Extract ## Model section
        JsonNode modelJson = sectionExtractor.extractSectionAsJson(content, "Model");
        if (modelJson == null) {
            throw new IOException("Missing required ## Model section in AGENT.md: " + agentPath);
        }
        
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setProvider(getRequiredField(modelJson, "provider", agentPath).asText());
        modelConfig.setName(getRequiredField(modelJson, "name", agentPath).asText());
        modelConfig.setTemperature(getRequiredField(modelJson, "temperature", agentPath).asDouble());
        manifest.setModel(modelConfig);
        
        // Extract ## Input section (optional)
        JsonNode inputJson = sectionExtractor.extractSectionAsJson(content, "Input");
        if (inputJson != null) {
            InputDefinition inputDef = new InputDefinition();
            inputDef.setSchema(parseSchema(inputJson));
            manifest.setInput(inputDef);
        }
        
        // Extract ## Output section (optional)
        JsonNode outputJson = sectionExtractor.extractSectionAsJson(content, "Output");
        if (outputJson != null) {
            OutputDefinition outputDef = new OutputDefinition();
            outputDef.setSchema(parseSchema(outputJson));
            manifest.setOutput(outputDef);
        }
        
        // Extract ## Instructions section
        String instructionsText = sectionExtractor.extractSectionText(content, "Instructions");
        if (instructionsText == null || instructionsText.isBlank()) {
            throw new IOException("Missing required ## Instructions section in AGENT.md: " + agentPath);
        }
        
        Instructions instructions = new Instructions();
        instructions.setSystemPrompt(instructionsText.trim());
        manifest.setInstructions(instructions);
        
        // Extract ## Examples section (optional)
        JsonNode examplesJson = sectionExtractor.extractSectionAsJson(content, "Examples");
        if (examplesJson != null && examplesJson.isArray()) {
            List<ExampleDefinition> examples = new ArrayList<>();
            examplesJson.forEach(exampleNode -> {
                ExampleDefinition example = jsonMapper.convertValue(exampleNode, ExampleDefinition.class);
                examples.add(example);
            });
            manifest.setExamples(examples);
        }
        
        log.debug("Parsed agent (Markdown format): {}", manifest.getAgent().getName());
        return manifest;
    }
    
    /**
     * Parse JSON Schema definition into SchemaDefinition object.
     */
    private SchemaDefinition parseSchema(JsonNode schemaJson) {
        SchemaDefinition schema = new SchemaDefinition();
        
        if (schemaJson.has("type")) {
            schema.setType(schemaJson.get("type").asText());
        }
        
        if (schemaJson.has("properties")) {
            Map<String, PropertyDefinition> properties = new LinkedHashMap<>();
            JsonNode propsNode = schemaJson.get("properties");
            propsNode.fields().forEachRemaining(entry -> {
                String propName = entry.getKey();
                JsonNode propNode = entry.getValue();
                
                PropertyDefinition propDef = new PropertyDefinition();
                if (propNode.has("type")) {
                    propDef.setType(propNode.get("type").asText());
                }
                if (propNode.has("required")) {
                    propDef.setRequired(propNode.get("required").asBoolean());
                }
                if (propNode.has("description")) {
                    propDef.setDescription(propNode.get("description").asText());
                }
                
                properties.put(propName, propDef);
            });
            schema.setProperties(properties);
        }
        
        return schema;
    }
    
    /**
     * Get required field from JSON node, throw clear error if missing.
     */
    private JsonNode getRequiredField(JsonNode node, String fieldName, Path agentPath) throws IOException {
        if (!node.has(fieldName)) {
            throw new IOException(String.format(
                "Missing required field '%s' in ## Configuration section: %s",
                fieldName, agentPath
            ));
        }
        return node.get(fieldName);
    }
}
