package org.hubbers.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.hubbers.agent.ArtifactCatalogInjector;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for executing natural language tasks using the universal task agent.
 * Handles dynamic tool injection, conversation management, and result processing.
 * Internal use only: prefer RuntimeFacade.runAgent() with {"request":"..."} pattern.
 */
@Slf4j
public class NaturalLanguageTaskService {
    
    private static final String UNIVERSAL_AGENT_NAME = "universal.task";

    private final RuntimeFacade runtimeFacade;
    private final ArtifactRepository repository;
    private final ArtifactCatalogInjector artifactCatalogInjector;
    private final ObjectMapper mapper;

    public NaturalLanguageTaskService(RuntimeFacade runtimeFacade,
                                     ObjectMapper mapper) {
        this.runtimeFacade = runtimeFacade;
        this.repository = runtimeFacade.getArtifactRepository();
        this.artifactCatalogInjector = new ArtifactCatalogInjector();
        this.mapper = mapper;
    }

    /**
     * Execute a natural language task (new conversation).
     * 
     * @param naturalLanguageRequest The user's request in natural language
     * @param context Optional context data
     * @return TaskExecutionResult with agent output and metadata
     */
    public TaskExecutionResult executeTask(String naturalLanguageRequest, JsonNode context) {
        String conversationId = UUID.randomUUID().toString();
        return executeTaskWithConversation(naturalLanguageRequest, context, conversationId);
    }

    /**
     * Execute a natural language task within an existing conversation.
     * 
     * @param naturalLanguageRequest The user's request in natural language
     * @param context Optional context data
     * @param conversationId Existing conversation ID for multi-turn dialogue
     * @return TaskExecutionResult with agent output and metadata
     */
    public TaskExecutionResult executeTaskWithConversation(String naturalLanguageRequest, 
                                                          JsonNode context,
                                                          String conversationId) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing task: '{}' (conversation: {})", 
                naturalLanguageRequest, conversationId);
            
            // Load universal task agent
            AgentManifest agent = repository.loadAgent(UNIVERSAL_AGENT_NAME);
            if (agent == null) {
                return TaskExecutionResult.failed(
                    "Universal task agent not found: " + UNIVERSAL_AGENT_NAME, 
                    conversationId);
            }
            
            // Load all available tools
            List<ToolManifest> allTools = repository.listTools().stream()
                .map(name -> {
                    try {
                        return repository.loadTool(name);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        log.warn("Failed to load tool {}: {}", name, e.getMessage());
                        return null;
                    }
                })
                .filter(tool -> tool != null)
                .toList();
            
            // Load all available agents
            List<AgentManifest> allAgents = repository.listAgents().stream()
                .map(name -> {
                    try {
                        return repository.loadAgent(name);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        log.warn("Failed to load agent {}: {}", name, e.getMessage());
                        return null;
                    }
                })
                .filter(a -> a != null)
                .toList();
            
            // Load all available pipelines
            List<PipelineManifest> allPipelines = repository.listPipelines().stream()
                .map(name -> {
                    try {
                        return repository.loadPipeline(name);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        log.warn("Failed to load pipeline {}: {}", name, e.getMessage());
                        return null;
                    }
                })
                .filter(p -> p != null)
                .toList();
            
            log.info("Loaded {} tools, {} agents, {} pipelines for task execution", 
                allTools.size(), allAgents.size(), allPipelines.size());
            
            // Check if agent has pre-defined tools (no filtering needed)
            @SuppressWarnings("unchecked")
            List<String> preDefinedTools = agent.getConfig() != null ? 
                (List<String>) agent.getConfig().get("tools") : null;
            boolean hasPreDefinedTools = preDefinedTools != null && !preDefinedTools.isEmpty();
            
            // Apply RAG-based filtering if agent doesn't have pre-defined tools
            if (!hasPreDefinedTools) {
                log.info("Applying RAG-based filtering for universal task agent");
                var filtered = artifactCatalogInjector.filterByRelevance(
                    naturalLanguageRequest,
                    allTools, 
                    allAgents, 
                    allPipelines,
                    repository.getAllSkillMetadata(),
                    5  // top-5 per type
                );
                
                // Inject filtered artifacts
                artifactCatalogInjector.injectAllArtifacts(
                    agent, filtered.tools(), filtered.agents(), 
                    filtered.pipelines(), filtered.skills()
                );
            } else {
                log.info("Agent has pre-defined tools, skipping RAG filtering");
                // Inject all artifacts (respects pre-defined tools)
                artifactCatalogInjector.injectAllArtifacts(
                    agent, allTools, allAgents, allPipelines, 
                    repository.getAllSkillMetadata()
                );
            }
            
            // Build input JSON
            ObjectNode input = mapper.createObjectNode();
            input.put("request", naturalLanguageRequest);
            if (context != null) {
                input.set("context", context);
            }
            
            // Execute agent with ReAct loop
            RunResult runResult = runtimeFacade.executeAgenticAgent(
                agent, input, conversationId);
            
            if ( runResult.getStatus() != ExecutionStatus.SUCCESS) {
                TaskExecutionResult failedResult = TaskExecutionResult.failed(runResult.getError(), conversationId);
                failedResult.setExecutionTrace(runResult.getExecutionTrace());
                failedResult.setMetadata(runResult.getMetadata());
                return failedResult;
            }
            
            // Parse agent output
            JsonNode output = runResult.getOutput();
            
            // Extract fields from agent output
            JsonNode resultNode = output.has("result") ? output.get("result") : output;
            String reasoning = output.has("reasoning") ? output.get("reasoning").asText() : "";
            List<String> toolsUsed = new ArrayList<>();
            if (output.has("tools_used") && output.get("tools_used").isArray()) {
                output.get("tools_used").forEach(t -> toolsUsed.add(t.asText()));
            }
            
            // Extract iteration count from metadata
            int iterations = 0;
            if (runResult.getMetadata() != null && runResult.getMetadata().getDetails() != null) {
                String details = runResult.getMetadata().getDetails();
                if (details.contains("iterations=")) {
                    try {
                        String iterStr = details.substring(
                            details.indexOf("iterations=") + 11);
                        iterStr = iterStr.split(",")[0].trim();
                        iterations = Integer.parseInt(iterStr);
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        log.debug("Could not parse iteration count: {}", e.getMessage());
                    }
                }
            }
            
            TaskExecutionResult result = TaskExecutionResult.success(
                resultNode, reasoning, toolsUsed, iterations, conversationId);
            result.setMetadata(runResult.getMetadata());
            result.setExecutionTrace(runResult.getExecutionTrace());
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Task completed successfully in {}ms (iterations: {}, tools: {})", 
                duration, iterations, toolsUsed.size());
            
            return result;
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid task configuration: {}", e.getMessage(), e);
            return TaskExecutionResult.failed(
                "Invalid configuration: " + e.getMessage(), 
                conversationId);
        } catch (RuntimeException e) {
            log.error("Task execution failed: {}", e.getMessage(), e);
            return TaskExecutionResult.failed(
                "Task execution failed: " + e.getMessage(), 
                conversationId);
        }
    }

    /**
     * Gets available artifacts count for informational purposes.
     */
    public int getAvailableToolsCount() {
        return repository.listTools().size();
    }
    
    public int getAvailableAgentsCount() {
        return repository.listAgents().size();
    }
    
    public int getAvailablePipelinesCount() {
        return repository.listPipelines().size();
    }
    
    public int getTotalArtifactsCount() {
        return getAvailableToolsCount() + getAvailableAgentsCount() + getAvailablePipelinesCount();
    }
}