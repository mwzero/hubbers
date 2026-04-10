package org.hubbers.agent;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.manifest.skill.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for dynamically injecting all available artifacts (tools, agents, pipelines, skills) 
 * into an agent's configuration. This allows agents to have access to the full catalog
 * of executable artifacts without hardcoding names.
 * 
 * Supports RAG-based filtering to reduce context size by selecting only relevant artifacts.
 */
public class ArtifactCatalogInjector {
    private static final Logger logger = LoggerFactory.getLogger(ArtifactCatalogInjector.class);
    private static final int DEFAULT_TOP_K_ARTIFACTS = 5;

    /**
     * Injects all available tools into the agent's configuration.
     * Legacy method for backward compatibility - delegates to injectAllArtifacts.
     * 
     * @param agent The agent manifest to inject tools into
     * @param allTools List of all available tool manifests
     * @return The modified agent manifest (same instance)
     */
    public AgentManifest injectTools(AgentManifest agent, List<ToolManifest> allTools) {
        return injectAllArtifacts(agent, allTools, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Injects all available artifacts (tools, agents, pipelines, skills) into the agent's configuration.
     * Modifies the agent manifest in-place by updating its tool list.
     * 
     * @param agent The agent manifest to inject artifacts into
     * @param tools List of all available tool manifests
     * @param agents List of all available agent manifests
     * @param pipelines List of all available pipeline manifests
     * @param skills List of all available skill metadata (progressive disclosure)
     * @return The modified agent manifest (same instance)
     */
    public AgentManifest injectAllArtifacts(AgentManifest agent, 
                                           List<ToolManifest> tools,
                                           List<AgentManifest> agents, 
                                           List<PipelineManifest> pipelines,
                                           List<SkillMetadata> skills) {
        if ((tools == null || tools.isEmpty()) && 
            (agents == null || agents.isEmpty()) && 
            (pipelines == null || pipelines.isEmpty()) &&
            (skills == null || skills.isEmpty())) {
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
        
        // Extract skill names (lightweight metadata only - progressive disclosure)
        if (skills != null) {
            List<String> skillNames = skills.stream()
                .map(SkillMetadata::getName)
                .filter(name -> name != null)
                .collect(Collectors.toList());
            allArtifactNames.addAll(skillNames);
        }
        
        // Update agent's tool list
        if (agent.getConfig() == null) {
            agent.setConfig(new java.util.HashMap<>());
        }
        
        // Get or create tools list from config
        @SuppressWarnings("unchecked")
        List<String> configTools = (List<String>) agent.getConfig().get("tools");
        
        // Check for separate skills configuration
        @SuppressWarnings("unchecked")
        List<String> configSkills = (List<String>) agent.getConfig().get("skills");
        
        // Check if agent already has specific tools defined
        boolean hasPreDefinedTools = configTools != null && !configTools.isEmpty();
        boolean hasPreDefinedSkills = configSkills != null && !configSkills.isEmpty();
        
        // Build artifact list based on configuration
        List<String> artifactsToInject = new ArrayList<>();
        
        if (hasPreDefinedTools) {
            // Agent has specific tools - use only those
            artifactsToInject.addAll(configTools);
            logger.info("Agent {} using pre-defined {} tools", 
                agent.getAgent().getName(), configTools.size());
        } else {
            // No specific tools - inject all available
            artifactsToInject.addAll(allArtifactNames);
        }
        
        // Handle skills separately
        if (hasPreDefinedSkills) {
            // Agent has specific skills - add only those (unless already in tools list)
            for (String skill : configSkills) {
                if (!artifactsToInject.contains(skill)) {
                    artifactsToInject.add(skill);
                }
            }
            logger.info("Agent {} using pre-defined {} skills", 
                agent.getAgent().getName(), configSkills.size());
        } else if (hasPreDefinedTools) {
            // Tools are specific but skills are not - add all skills
            if (skills != null) {
                List<String> skillNames = skills.stream()
                    .map(SkillMetadata::getName)
                    .filter(name -> name != null && !artifactsToInject.contains(name))
                    .collect(Collectors.toList());
                artifactsToInject.addAll(skillNames);
                logger.info("Agent {} auto-injecting all {} skills", 
                    agent.getAgent().getName(), skillNames.size());
            }
        }
        
        // Update tools list with final artifacts
        if (configTools == null) {
            configTools = new ArrayList<>();
            agent.getConfig().put("tools", configTools);
        }
        
        // Replace with selected artifacts
        configTools.clear();
        configTools.addAll(artifactsToInject);
        
        // Count what was actually injected
        int actualToolCount = hasPreDefinedTools ? configTools.size() : 
            (tools != null ? tools.size() : 0);
        int actualAgentCount = hasPreDefinedTools ? 0 : 
            (agents != null ? agents.size() - 1 : 0); // -1 to exclude self
        int actualPipelineCount = hasPreDefinedTools ? 0 : 
            (pipelines != null ? pipelines.size() : 0);
        int actualSkillCount = hasPreDefinedSkills ? configSkills.size() : 
            (skills != null ? skills.size() : 0);
        
        logger.info("Injected {} artifacts into agent {} (tools={}, agents={}, pipelines={}, skills={})", 
            artifactsToInject.size(), agent.getAgent().getName(), 
            actualToolCount, actualAgentCount, actualPipelineCount, actualSkillCount);
        
        return agent;
    }
    
    /**
     * Gets a summary of available artifacts for logging/debugging.
     */
    public String getArtifactsSummary(List<ToolManifest> tools, 
                                     List<AgentManifest> agents, 
                                     List<PipelineManifest> pipelines,
                                     List<SkillMetadata> skills) {
        int toolCount = tools != null ? tools.size() : 0;
        int agentCount = agents != null ? agents.size() : 0;
        int pipelineCount = pipelines != null ? pipelines.size() : 0;
        int skillCount = skills != null ? skills.size() : 0;
        
        return String.format("Tools: %d, Agents: %d, Pipelines: %d, Skills: %d (Total: %d artifacts)",
            toolCount, agentCount, pipelineCount, skillCount, 
            toolCount + agentCount + pipelineCount + skillCount);
    }
    
    /**
     * Filter artifacts based on relevance to user request (RAG-based selection).
     * Returns only the top-K most relevant artifacts to reduce context size.
     * 
     * @param userRequest The natural language request from user
     * @param tools All available tools
     * @param agents All available agents
     * @param pipelines All available pipelines
     * @param skills All available skills
     * @param topK Number of artifacts to select per type
     * @return Filtered artifacts
     */
    public FilteredArtifacts filterByRelevance(String userRequest,
                                              List<ToolManifest> tools,
                                              List<AgentManifest> agents,
                                              List<PipelineManifest> pipelines,
                                              List<SkillMetadata> skills,
                                              int topK) {
        logger.info("Filtering artifacts by relevance to request: '{}'", userRequest);
        
        List<ToolManifest> filteredTools = filterTools(userRequest, tools, topK);
        List<AgentManifest> filteredAgents = filterAgents(userRequest, agents, topK);
        List<PipelineManifest> filteredPipelines = filterPipelines(userRequest, pipelines, topK);
        List<SkillMetadata> filteredSkills = filterSkills(userRequest, skills, topK);
        
        int total = filteredTools.size() + filteredAgents.size() + 
                   filteredPipelines.size() + filteredSkills.size();
        
        logger.info("Filtered to {} relevant artifacts (tools={}, agents={}, pipelines={}, skills={})",
            total, filteredTools.size(), filteredAgents.size(), 
            filteredPipelines.size(), filteredSkills.size());
        
        return new FilteredArtifacts(filteredTools, filteredAgents, filteredPipelines, filteredSkills);
    }
    
    private List<ToolManifest> filterTools(String request, List<ToolManifest> all, int topK) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        
        List<ScoredItem<ToolManifest>> scored = all.stream()
            .map(t -> new ScoredItem<>(t, calculateRelevance(request, 
                t.getTool().getName(), t.getTool().getDescription())))
            .collect(Collectors.toList());
        
        scored.sort(Comparator.comparingDouble((ScoredItem<ToolManifest> s) -> s.score).reversed());
        
        return scored.stream()
            .limit(topK)
            .peek(s -> logger.debug("  Tool: {} (score: {:.2f})", s.item.getTool().getName(), s.score))
            .map(s -> s.item)
            .collect(Collectors.toList());
    }
    
    private List<AgentManifest> filterAgents(String request, List<AgentManifest> all, int topK) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        
        List<ScoredItem<AgentManifest>> scored = all.stream()
            .map(a -> new ScoredItem<>(a, calculateRelevance(request,
                a.getAgent().getName(), a.getAgent().getDescription())))
            .collect(Collectors.toList());
        
        scored.sort(Comparator.comparingDouble((ScoredItem<AgentManifest> s) -> s.score).reversed());
        
        return scored.stream()
            .limit(topK)
            .peek(s -> logger.debug("  Agent: {} (score: {:.2f})", s.item.getAgent().getName(), s.score))
            .map(s -> s.item)
            .collect(Collectors.toList());
    }
    
    private List<PipelineManifest> filterPipelines(String request, List<PipelineManifest> all, int topK) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        
        List<ScoredItem<PipelineManifest>> scored = all.stream()
            .map(p -> new ScoredItem<>(p, calculateRelevance(request,
                p.getPipeline().getName(), p.getPipeline().getDescription())))
            .collect(Collectors.toList());
        
        scored.sort(Comparator.comparingDouble((ScoredItem<PipelineManifest> s) -> s.score).reversed());
        
        return scored.stream()
            .limit(topK)
            .peek(s -> logger.debug("  Pipeline: {} (score: {:.2f})", s.item.getPipeline().getName(), s.score))
            .map(s -> s.item)
            .collect(Collectors.toList());
    }
    
    private List<SkillMetadata> filterSkills(String request, List<SkillMetadata> all, int topK) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        
        List<ScoredItem<SkillMetadata>> scored = all.stream()
            .map(sk -> new ScoredItem<>(sk, calculateRelevance(request, sk.getName(), sk.getDescription())))
            .collect(Collectors.toList());
        
        scored.sort(Comparator.comparingDouble((ScoredItem<SkillMetadata> s) -> s.score).reversed());
        
        return scored.stream()
            .limit(topK)
            .peek(s -> logger.debug("  Skill: {} (score: {:.2f})", s.item.getName(), s.score))
            .map(s -> s.item)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate relevance score using keyword matching and semantic patterns.
     */
    private double calculateRelevance(String userRequest, String name, String description) {
        if (userRequest == null || userRequest.isBlank()) return 0.0;
        
        String request = userRequest.toLowerCase();
        String nameL = name != null ? name.toLowerCase() : "";
        String descL = description != null ? description.toLowerCase() : "";
        
        double score = 0.0;
        
        // Extract keywords
        Set<String> requestWords = extractKeywords(request);
        Set<String> nameWords = extractKeywords(nameL);
        Set<String> descWords = extractKeywords(descL);
        
        // Name keyword match (weight: 3.0)
        for (String word : requestWords) {
            if (nameWords.contains(word)) score += 3.0;
        }
        
        // Description keyword match (weight: 1.0)
        for (String word : requestWords) {
            if (descWords.contains(word)) score += 1.0;
        }
        
        // Partial name match (weight: 2.0)
        for (String word : requestWords) {
            if (nameL.contains(word)) score += 2.0;
        }
        
        // Semantic patterns
        score += checkPatterns(request, nameL, descL);
        
        return score;
    }
    
    private Set<String> extractKeywords(String text) {
        Set<String> stopwords = Set.of("a", "an", "the", "is", "are", "from", "to", "and", "or");
        return Arrays.stream(text.split("[\\s.,-]+"))
            .map(w -> w.trim().toLowerCase())
            .filter(w -> w.length() > 2 && !stopwords.contains(w))
            .collect(Collectors.toSet());
    }
    
    private double checkPatterns(String req, String name, String desc) {
        double s = 0.0;
        if (matches(req, "feed|rss|news") && matches(name + " " + desc, "rss|feed")) s += 2.0;
        if (matches(req, "sentiment|emotion") && matches(name + " " + desc, "sentiment")) s += 2.0;
        if (matches(req, "translate|translation") && matches(name + " " + desc, "translat")) s += 2.0;
        if (matches(req, "summar|brief") && matches(name + " " + desc, "summar")) s += 2.0;
        if (matches(req, "file|read|write|save") && matches(name + " " + desc, "file")) s += 1.5;
        if (matches(req, "csv|data|table") && matches(name + " " + desc, "csv")) s += 1.5;
        if (matches(req, "extract") && matches(name + " " + desc, "extract|fetch|get")) s += 1.5;
        return s;
    }
    
    private boolean matches(String text, String pattern) {
        return Arrays.stream(pattern.split("\\|")).anyMatch(text::contains);
    }
    
    private static class ScoredItem<T> {
        final T item;
        final double score;
        
        ScoredItem(T item, double score) {
            this.item = item;
            this.score = score;
        }
    }
    
    public static class FilteredArtifacts {
        private final List<ToolManifest> tools;
        private final List<AgentManifest> agents;
        private final List<PipelineManifest> pipelines;
        private final List<SkillMetadata> skills;
        
        public FilteredArtifacts(List<ToolManifest> tools, List<AgentManifest> agents,
                                List<PipelineManifest> pipelines, List<SkillMetadata> skills) {
            this.tools = tools;
            this.agents = agents;
            this.pipelines = pipelines;
            this.skills = skills;
        }
        
        public List<ToolManifest> tools() { return tools; }
        public List<AgentManifest> agents() { return agents; }
        public List<PipelineManifest> pipelines() { return pipelines; }
        public List<SkillMetadata> skills() { return skills; }
    }
}
