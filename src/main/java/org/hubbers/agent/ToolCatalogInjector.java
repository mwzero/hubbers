package org.hubbers.agent;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.tool.ToolManifest;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for dynamically injecting all available tools into an agent's configuration.
 * This allows agents to have access to the full tool catalog without hardcoding tool names.
 */
@Slf4j
public class ToolCatalogInjector {

    /**
     * Injects all available tools into the agent's configuration.
     * Modifies the agent manifest in-place by updating its tool list.
     * 
     * @param agent The agent manifest to inject tools into
     * @param allTools List of all available tool manifests
     * @return The modified agent manifest (same instance)
     */
    public AgentManifest injectTools(AgentManifest agent, List<ToolManifest> allTools) {
        if (allTools == null || allTools.isEmpty()) {
            log.warn("No tools available to inject into agent {}", agent.getAgent().getName());
            return agent;
        }
        
        // Extract tool names
        List<String> toolNames = allTools.stream()
            .map(tool -> tool.getTool().getName())
            .collect(Collectors.toList());
        
        // Update agent's tool list
        if (agent.getConfig() == null) {
            agent.setConfig(new HashMap<String, Object>());
        }
        
        if (agent.getTools() == null) {
            agent.setTools(new ArrayList<>());
        }
        
        // Replace with full catalog
        agent.getTools().clear();
        agent.getTools().addAll(toolNames);
        
        log.info("Injected {} tools into agent {}: {}", 
            toolNames.size(), agent.getAgent().getName(), toolNames);
        
        return agent;
    }
    
    /**
     * Gets a summary of available tools for logging/debugging.
     */
    public String getToolsSummary(List<ToolManifest> tools) {
        if (tools == null || tools.isEmpty()) {
            return "No tools available";
        }
        
        return tools.stream()
            .map(t -> t.getTool().getName() + " (" + t.getType() + ")")
            .collect(Collectors.joining(", "));
    }
}