package org.hubbers.agent;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for dynamically injecting all available artifacts (tools, agents, pipelines) 
 * into an agent's configuration. This allows agents to have access to the full catalog
 * of executable artifacts without hardcoding names.
 */
public class ArtifactCatalogInjector {
    private static final Logger logger = LoggerFactory.getLogger(ArtifactCatalogInjector.class);

    /**
     * Injects all available tools into the agent's configuration.
     * Legacy method for backward compatibility - delegates to injectAllArtifacts.
     * 
     * @param agent The agent manifest to inject tools into
     * @param allTools List of all available tool manifests
     * @return The modified agent manifest (same instance)
     */
    public AgentManifest injectTools(AgentManifest agent, List<ToolManifest> allTools) {
        return injectAllArtifacts(agent, allTools, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Injects all available artifacts (tools, agents, pipelines) into the agent's configuration.
     * Modifies the agent manifest in-place by updating its tool list.
     * 
     * @param agent The agent manifest to inject artifacts into
     * @param tools List of all available tool manifests
     * @param agents List of all available agent manifests
     * @param pipelines List of all available pipeline manifests
     * @return The modified agent manifest (same instance)
     */
    public AgentManifest injectAllArtifacts(AgentManifest agent, 
                                           List<ToolManifest> tools,
                                           List<AgentManifest> agents, 
                                           List<PipelineManifest> pipelines) {
        if ((tools == null || tools.isEmpty()) && 
            (agents == null || agents.isEmpty()) && 
            (pipelines == null || pipelines.isEmpty())) {
            logger.warn("No artifacts available to inject into agent {}", agent.getAgent().getName());
            return agent;
        }
        
        List<String> allArtifactNames = new ArrayList<>();
        
        // Extract tool names
        if (tools != null) {
            List<String> toolNames = tools.stream()
                .map(tool -> tool.getTool().getName())
                .collect(Collectors.toList());
            allArtifactNames.addAll(toolNames);
        }
        
        // Extract agent names (filter out the current agent to avoid self-calling)
        if (agents != null) {
            List<String> agentNames = agents.stream()
                .map(a -> a.getAgent() != null ? a.getAgent().getName() : null)
                .filter(name -> name != null && !name.equals(agent.getAgent().getName()))
                .collect(Collectors.toList());
            allArtifactNames.addAll(agentNames);
        }
        
        // Extract pipeline names
        if (pipelines != null) {
            List<String> pipelineNames = pipelines.stream()
                .map(p -> p.getPipeline() != null ? p.getPipeline().getName() : null)
                .filter(name -> name != null)
                .collect(Collectors.toList());
            allArtifactNames.addAll(pipelineNames);
        }
        
        // Update agent's tool list
        if (agent.getConfig() == null) {
            agent.setConfig(new java.util.HashMap<>());
        }
        
        // Get or create tools list from config
        @SuppressWarnings("unchecked")
        List<String> configTools = (List<String>) agent.getConfig().get("tools");
        if (configTools == null) {
            configTools = new ArrayList<>();
            agent.getConfig().put("tools", configTools);
        }
        
        // Replace with full catalog
        configTools.clear();
        configTools.addAll(allArtifactNames);
        
        int toolCount = tools != null ? tools.size() : 0;
        int agentCount = agents != null ? agents.size() - 1 : 0; // -1 to exclude self
        int pipelineCount = pipelines != null ? pipelines.size() : 0;
        
        logger.info("Injected {} tools, {} agents, {} pipelines into agent {} (total: {} artifacts)", 
            toolCount, agentCount, pipelineCount, agent.getAgent().getName(), allArtifactNames.size());
        
        return agent;
    }
    
    /**
     * Gets a summary of available artifacts for logging/debugging.
     */
    public String getArtifactsSummary(List<ToolManifest> tools, 
                                     List<AgentManifest> agents, 
                                     List<PipelineManifest> pipelines) {
        int toolCount = tools != null ? tools.size() : 0;
        int agentCount = agents != null ? agents.size() : 0;
        int pipelineCount = pipelines != null ? pipelines.size() : 0;
        
        return String.format("Tools: %d, Agents: %d, Pipelines: %d (Total: %d artifacts)",
            toolCount, agentCount, pipelineCount, toolCount + agentCount + pipelineCount);
    }
}
