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
 * <p>Drivers are automatically registered in {@code ToolExecutor} based on
 * their type() identifier. The executor handles validation and routing.</p>
 * 
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
     * <p>Drivers should throw {@link ToolExecutionException} for categorized
     * failures or {@link IllegalArgumentException} for configuration errors.</p>
     * 
     * @param manifest the tool manifest containing configuration
     * @param input the input data (already validated against input schema)
     * @return the output data (will be validated against output schema)
     * @throws ToolExecutionException if execution fails with a categorized error
     * @throws IllegalArgumentException if configuration is invalid
     */
    JsonNode execute(ToolManifest manifest, JsonNode input);
}
