package org.hubbers.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.hubbers.agent.ArtifactCatalogInjector;
import org.hubbers.agent.ToolCatalogInjector;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for executing natural language tasks using the universal task agent.
 * Handles dynamic tool injection, conversation management, and result processing.
 */
public class NaturalLanguageTaskService {
    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageTaskService.class);
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
            logger.info("Executing task: '{}' (conversation: {})", 
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
                    } catch (Exception e) {
                        logger.warn("Failed to load tool {}: {}", name, e.getMessage());
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
                    } catch (Exception e) {
                        logger.warn("Failed to load agent {}: {}", name, e.getMessage());
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
                    } catch (Exception e) {
                        logger.warn("Failed to load pipeline {}: {}", name, e.getMessage());
                        return null;
                    }
                })
                .filter(p -> p != null)
                .toList();
            
            logger.info("Loaded {} tools, {} agents, {} pipelines for task execution", 
                allTools.size(), allAgents.size(), allPipelines.size());
            
            // Inject all artifacts into agent
            artifactCatalogInjector.injectAllArtifacts(agent, allTools, allAgents, allPipelines);
            
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
                return TaskExecutionResult.failed(runResult.getError(), conversationId);
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
                    } catch (Exception e) {
                        logger.debug("Could not parse iteration count: {}", e.getMessage());
                    }
                }
            }
            
            TaskExecutionResult result = TaskExecutionResult.success(
                resultNode, reasoning, toolsUsed, iterations, conversationId);
            result.setMetadata(runResult.getMetadata());
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Task completed successfully in {}ms (iterations: {}, tools: {})", 
                duration, iterations, toolsUsed.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Task execution failed: {}", e.getMessage(), e);
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