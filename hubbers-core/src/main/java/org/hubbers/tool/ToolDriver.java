package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.manifest.tool.ToolManifest;

/**
 * Interface for tool drivers that execute specific tool types.
 * 
 * <p>Tool drivers provide the actual implementation for different tool types
 * (HTTP, Docker, RSS, etc.). Each driver is responsible for:</p>
 * <ul>
 *   <li>Declaring its tool type identifier</li>
 *   <li>Executing the tool logic with given manifest and input</li>
 *   <li>Returning structured output as JsonNode</li>
 * </ul>
 * 
 * <p>Drivers are automatically registered in {@link ToolExecutor} based on
 * their type() identifier. The executor handles validation and routing.</p>
 * 
 * <p>Example implementation:
 * <pre>{@code
 * public class MyToolDriver implements ToolDriver {
 *     @Override
 *     public String type() {
 *         return "custom";
 *     }
 *     
 *     @Override
 *     public JsonNode execute(ToolManifest manifest, JsonNode input) {
 *         // Extract config from manifest
 *         String apiKey = manifest.getConfig().get("api_key").toString();
 *         
 *         // Execute tool logic
 *         String result = callExternalService(apiKey, input);
 *         
 *         // Return structured output
 *         ObjectNode output = mapper.createObjectNode();
 *         output.put("result", result);
 *         return output;
 *     }
 * }
 * }</pre>
 * 
 * <p>Built-in tool drivers:</p>
 * <ul>
 *   <li><b>http</b> - {@link HttpToolDriver} - HTTP API calls</li>
 *   <li><b>docker</b> - {@link DockerToolDriver} - Docker container execution</li>
 *   <li><b>rss</b> - {@link RssToolDriver} - RSS/Atom feed parsing</li>
 *   <li><b>firecrawl</b> - {@link FirecrawlToolDriver} - Web scraping</li>
 *   <li><b>shell</b> - {@link ShellExecToolDriver} - Shell command execution</li>
 *   <li><b>csv</b> - CSV read/write operations</li>
 *   <li><b>lucene</b> - Lucene-based vector and key-value storage</li>
 * </ul>
 * 
 * @see ToolExecutor
 * @see ToolManifest
 * @since 0.1.0
 */
public interface ToolDriver {
    /**
     * Get the tool type identifier.
     * 
     * <p>This identifier must match the "type" field in tool.yaml manifests.
     * It's used by ToolExecutor to route execution to the correct driver.</p>
     * 
     * @return the tool type (e.g., "http", "docker", "rss")
     */
    String type();
    
    /**
     * Execute the tool with given manifest and input data.
     * 
     * <p>Input and output validation is handled by ToolExecutor before/after
     * calling this method. Drivers should focus on tool-specific logic.</p>
     * 
     * <p>Drivers should throw {@link IllegalArgumentException} for configuration
     * errors and {@link IllegalStateException} for execution failures.</p>
     * 
     * @param manifest the tool manifest containing configuration
     * @param input the input data (already validated against input schema)
     * @return the output data (will be validated against output schema)
     * @throws IllegalArgumentException if configuration is invalid
     * @throws IllegalStateException if execution fails
     */
    JsonNode execute(ToolManifest manifest, JsonNode input);
}

