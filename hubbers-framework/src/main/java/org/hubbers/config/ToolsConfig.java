package org.hubbers.config;

import java.util.HashMap;
import java.util.Map;

public class ToolsConfig {
    private Map<String, Map<String, String>> tools = new HashMap<>();

    public Map<String, Map<String, String>> getTools() {
        return tools;
    }

    public void setTools(Map<String, Map<String, String>> tools) {
        this.tools = tools;
    }

    /**
     * Get configuration value for a specific tool and key.
     * @param toolType the tool type (e.g., "firecrawl", "http")
     * @param key the configuration key (e.g., "api_key")
     * @return the configuration value or null if not found
     */
    public String get(String toolType, String key) {
        Map<String, String> toolConfig = tools.get(toolType);
        if (toolConfig == null) {
            return null;
        }
        return toolConfig.get(key);
    }
}
