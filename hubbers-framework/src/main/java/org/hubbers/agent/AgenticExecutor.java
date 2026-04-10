package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.agent.memory.ConversationMemory;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.ExecutionMetadata;
import org.hubbers.execution.RunResult;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.*;
import org.hubbers.pipeline.PipelineExecutor;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.validation.SchemaValidator;
import org.hubbers.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * AgenticExecutor implements the ReAct loop for agents with function calling capabilities.
 * Unlike AgentExecutor (single-shot), this executor:
 * - Maintains conversation history
 * - Allows agents to call tools (tasks) autonomously
 * - Implements multi-step reasoning (ReAct pattern)
 * - Supports memory persistence
 */
public class AgenticExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgenticExecutor.class);
    
    private final ModelProviderRegistry modelProviderRegistry;
    private final ToolExecutor toolExecutor;
    private final ArtifactRepository repository;
    private final SchemaValidator schemaValidator;
    private final ConversationMemory conversationMemory;
    private final ObjectMapper mapper;
    private final ArtifactToFunctionConverter artifactConverter;
    private final PipelineExecutor pipelineExecutor;
    private final AgentExecutor agentExecutor;
    
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final long DEFAULT_TIMEOUT_MS = 60000;

    public AgenticExecutor(ModelProviderRegistry modelProviderRegistry,
                          ToolExecutor toolExecutor,
                          ArtifactRepository repository,
                          SchemaValidator schemaValidator,
                          ConversationMemory conversationMemory,
                          PipelineExecutor pipelineExecutor,
                          AgentExecutor agentExecutor,
                          ObjectMapper mapper) {
        this.modelProviderRegistry = modelProviderRegistry;
        this.toolExecutor = toolExecutor;
        this.repository = repository;
        this.schemaValidator = schemaValidator;
        this.conversationMemory = conversationMemory;
        this.pipelineExecutor = pipelineExecutor;
        this.agentExecutor = agentExecutor;
        this.mapper = mapper;
        this.artifactConverter = new ArtifactToFunctionConverter(mapper);
    }

    /**
     * Execute an agentic agent with optional conversation ID for multi-turn.
     */
    public RunResult execute(AgentManifest manifest, JsonNode input, String conversationId) {
        long startTime = System.currentTimeMillis();
        
        // Generate conversation ID if not provided
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString();
        }
        
        // Validate input
        ValidationResult inputValidation = schemaValidator.validate(input, manifest.getInput().getSchema());
        if (!inputValidation.isValid()) {
            return RunResult.failed(String.join(", ", inputValidation.getErrors()));
        }
        
        // Load conversation history
        List<Message> history = conversationMemory.loadHistory(conversationId);
        
        // Prepare function definitions from available tools
        List<FunctionDefinition> functionDefinitions = buildFunctionDefinitions(manifest);
        
        // Add system prompt if first message (with artifact catalog injection)
        if (history.isEmpty() && manifest.getInstructions() != null) {
            String systemPrompt = manifest.getInstructions().getSystemPrompt();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                // Inject artifacts catalog if placeholder exists
                if (systemPrompt.contains("{{ARTIFACTS_CATALOG}}")) {
                    String artifactsCatalog = buildArtifactsCatalog(functionDefinitions);
                    log.debug("Injecting concise artifacts catalog ({} total artifacts)", functionDefinitions.size());
                    log.trace("Artifacts catalog:\n{}", artifactsCatalog);
                    systemPrompt = systemPrompt.replace("{{ARTIFACTS_CATALOG}}", artifactsCatalog);
                }
                
                log.info("=== SYSTEM PROMPT INJECTED ===");
                log.debug("System prompt length: {} characters", systemPrompt.length());
                if (log.isTraceEnabled()) {
                    log.trace("Full system prompt:\n{}", systemPrompt);
                }
                
                history.add(Message.system(systemPrompt));
            }
        }
        
        // Add user input
        String userContent = input.toString();
        Message userMessage = Message.user(userContent);
        history.add(userMessage);
        conversationMemory.saveMessage(conversationId, userMessage);
        
        // Log system prompt and available functions (TRACE level)
        if (log.isTraceEnabled() && !history.isEmpty()) {
            log.trace("=== AGENT SYSTEM PROMPT ===");
            for (Message msg : history) {
                if ("system".equals(msg.getRole())) {
                    log.trace(msg.getContent());
                }
            }
            log.trace("=== AVAILABLE FUNCTIONS ({}) ===", functionDefinitions.size());
            for (FunctionDefinition func : functionDefinitions) {
                log.trace("Function: {} - {}", func.getName(), func.getDescription());
            }
            log.trace("==============================");
        }
        
        // ReAct loop
        int maxIterations = getMaxIterations(manifest);
        long timeoutMs = getTimeoutMs(manifest);
        
        // Two-phase ReAct pattern state
        boolean planningPhase = true;  // Start in planning phase
        List<FunctionDefinition> currentFunctions = functionDefinitions;
        
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return RunResult.failed("Agent execution timed out after " + timeoutMs + "ms");
            }
            
            // Phase 1 (iteration 0): Planning - agent declares which artifacts to use
            if (iteration == 0 && planningPhase) {
                log.info("=== PHASE 1: PLANNING (Iteration {}) ===", iteration);
                log.info("Agent will select artifacts from catalog");
                log.info("Available functions: plan_task ONLY (artifact specs will be loaded after planning)");
                currentFunctions = List.of(createPlanningFunction());
            } else if (planningPhase) {
                log.info("=== PHASE 2: EXECUTION (Iteration {}) ===", iteration);
                log.info("Available functions: {} selected artifacts", currentFunctions.size());
            } else {
                log.debug("=== Iteration {} (continuing execution) ===", iteration);
            }
            
            // Build model request with conversation history and available functions
            ModelRequest request = new ModelRequest();
            request.setModel(manifest.getModel().getName());
            request.setTemperature(manifest.getModel().getTemperature());
            request.setMessages(new ArrayList<>(history));
            request.setFunctions(currentFunctions);
            
            // Log function availability
            log.debug("Sending {} functions to model:", currentFunctions.size());
            for (FunctionDefinition func : currentFunctions) {
                log.debug("  - {} : {}", func.getName(), func.getDescription());
            }
            
            // Call LLM
            log.debug("Calling model provider: {}", manifest.getModel().getProvider());
            ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
            long callStart = System.currentTimeMillis();
            ModelResponse response = provider.generate(request);
            long callDuration = System.currentTimeMillis() - callStart;
            log.info("Model response received in {}ms, finish_reason: {}", callDuration, response.getFinishReason());
            
            // Add assistant response to history
            Message assistantMessage = Message.assistant(response.getContent());
            history.add(assistantMessage);
            conversationMemory.saveMessage(conversationId, assistantMessage);
            
            // Check finish reason
            if ("stop".equals(response.getFinishReason()) || response.getFunctionCalls() == null || response.getFunctionCalls().isEmpty()) {
                // Agent completed - parse final output
                try {
                    log.info("=== AGENT OUTPUT RECEIVED ===");
                    log.info("Raw response content:\n{}", response.getContent());
                    
                    JsonNode output = mapper.readTree(response.getContent());
                    log.info("Parsed JSON structure: {}", output.toPrettyString());
                    log.info("===========================");
                    
                    ValidationResult outputValidation = schemaValidator.validate(output, manifest.getOutput().getSchema());
                    if (!outputValidation.isValid()) {
                        log.error("=== OUTPUT VALIDATION FAILED ===");
                        log.error("Expected schema: {}", mapper.writeValueAsString(manifest.getOutput().getSchema()));
                        log.error("Received output: {}", output.toPrettyString());
                        log.error("Validation errors: {}", String.join(", ", outputValidation.getErrors()));
                        log.error("================================");
                        return RunResult.failed(String.join(", ", outputValidation.getErrors()));
                    }
                    
                    RunResult result = RunResult.success(output);
                    ExecutionMetadata metadata = new ExecutionMetadata();
                    metadata.setStartedAt(startTime);
                    metadata.setEndedAt(System.currentTimeMillis());
                    metadata.setDetails("conversationId=" + conversationId + ", iterations=" + (iteration + 1));
                    result.setMetadata(metadata);
                    return result;
                } catch (Exception e) {
                    return RunResult.failed("Failed to parse agent output: " + e.getMessage());
                }
            }
            
            // Execute function calls (tool invocations)
            if ("function_call".equals(response.getFinishReason()) && response.getFunctionCalls() != null) {
                for (FunctionCall functionCall : response.getFunctionCalls()) {
                    
                    // Special handling for plan_task function call (Phase 1 → Phase 2 transition)
                    if ("plan_task".equals(functionCall.getName())) {
                        log.info("=== PHASE 1 COMPLETE: Plan received ===");
                        JsonNode plan = functionCall.getArguments();
                        
                        // Debug: log the raw plan arguments
                        log.debug("plan_task arguments received: {}", plan != null ? plan.toString() : "null");
                        if (log.isTraceEnabled() && plan != null) {
                            log.trace("plan_task full structure: {}", plan.toPrettyString());
                        }
                        
                        List<String> selectedArtifacts = extractArtifactNames(plan);
                        
                        if (selectedArtifacts.isEmpty()) {
                            log.warn("Agent selected no artifacts - falling back to all available");
                            currentFunctions = functionDefinitions;
                        } else {
                            // Build full specifications for selected artifacts only
                            currentFunctions = buildSelectedFunctionDefinitions(selectedArtifacts);
                            
                            if (currentFunctions.isEmpty()) {
                                return RunResult.failed("None of the selected artifacts were found: " + selectedArtifacts);
                            }
                        }
                        
                        // Transition to execution phase
                        planningPhase = false;
                        
                        // Add system message confirming selection and instructing execution
                        String confirmationMsg = String.format(
                            "✓ Planning complete. Selected artifacts: %s\n\n" +
                            "Now in EXECUTION phase. You have full specifications for these artifacts.\n" +
                            "IMPORTANT: Do NOT call plan_task again. Call the actual artifacts (like rss.fetch) with proper parameters.\n" +
                            "Return your final result as JSON: {\"result\": {...}, \"tools_used\": [...], \"reasoning\": \"...\"}",
                            selectedArtifacts
                        );
                        Message confirmation = Message.system(confirmationMsg);
                        history.add(confirmation);
                        conversationMemory.saveMessage(conversationId, confirmation);
                        
                        log.info("=== PHASE 2 READY: {} artifacts loaded ===", currentFunctions.size());
                        continue; // Next iteration with full specs
                    }
                    
                    // Regular artifact execution
                    RunResult toolResult = executeFunctionCall(functionCall);
                    
                    // Add tool result to history
                    String toolContent = (toolResult.getStatus() == ExecutionStatus.SUCCESS) ? 
                        toolResult.getOutput().toString() : 
                        "Error: " + toolResult.getError();
                    
                    Message toolMessage = Message.tool(functionCall.getId(), functionCall.getName(), toolContent);
                    history.add(toolMessage);
                    conversationMemory.saveMessage(conversationId, toolMessage);
                }
            }
        }
        
        return RunResult.failed("Agent exceeded maximum iterations (" + maxIterations + ")");
    }

    private RunResult executeFunctionCall(FunctionCall functionCall) {
        String artifactName = functionCall.getName();
        JsonNode arguments = functionCall.getArguments();
        
        try {
            // Try loading as tool first
            ToolManifest tool = repository.loadTool(artifactName);
            if (tool != null) {
                return toolExecutor.execute(tool, arguments);
            }
            
            // Try loading as agent
            AgentManifest agent = repository.loadAgent(artifactName);
            if (agent != null) {
                if (agentExecutor != null) {
                    // Execute agent in single-shot mode (no nested conversation)
                    return agentExecutor.execute(agent, arguments);
                } else {
                    return RunResult.failed("Agent execution not available: AgentExecutor not initialized");
                }
            }
            
            // Try loading as pipeline
            PipelineManifest pipeline = repository.loadPipeline(artifactName);
            if (pipeline != null) {
                if (pipelineExecutor != null) {
                    return pipelineExecutor.execute(pipeline, arguments);
                } else {
                    return RunResult.failed("Pipeline execution not available: PipelineExecutor not initialized");
                }
            }
            
            // Artifact not found in any repository
            return RunResult.failed("Artifact not found: " + artifactName);
            
        } catch (Exception e) {
            ObjectNode errorOutput = mapper.createObjectNode();
            errorOutput.put("error", "Artifact execution failed: " + e.getMessage());
            return RunResult.failed(e.getMessage());
        }
    }

    private List<FunctionDefinition> buildFunctionDefinitions(AgentManifest manifest) {
        List<FunctionDefinition> definitions = new ArrayList<>();
        
        // Check if agent specifies artifacts (tools/agents/pipelines) in config
        Object toolsConfig = manifest.getConfig() != null ? manifest.getConfig().get("tools") : null;
        if (toolsConfig instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> artifactNames = (List<String>) toolsConfig;
            
            for (String artifactName : artifactNames) {
                try {
                    // Try loading as tool
                    ToolManifest tool = repository.loadTool(artifactName);
                    if (tool != null) {
                        FunctionDefinition definition = artifactConverter.convertTool(tool);
                        definitions.add(definition);
                        continue;
                    }
                    
                    // Try loading as agent
                    AgentManifest agent = repository.loadAgent(artifactName);
                    if (agent != null) {
                        FunctionDefinition definition = artifactConverter.convertAgent(agent);
                        definitions.add(definition);
                        continue;
                    }
                    
                    // Try loading as pipeline
                    PipelineManifest pipeline = repository.loadPipeline(artifactName);
                    if (pipeline != null) {
                        FunctionDefinition definition = artifactConverter.convertPipeline(pipeline);
                        definitions.add(definition);
                    }
                } catch (Exception e) {
                    // Artifact not found or error loading, skip
                }
            }
        }
        
        return definitions;
    }

    private int getMaxIterations(AgentManifest manifest) {
        if (manifest.getConfig() != null && manifest.getConfig().containsKey("max_iterations")) {
            return Integer.parseInt(manifest.getConfig().get("max_iterations").toString());
        }
        return DEFAULT_MAX_ITERATIONS;
    }

    private long getTimeoutMs(AgentManifest manifest) {
        if (manifest.getConfig() != null && manifest.getConfig().containsKey("timeout_seconds")) {
            return Long.parseLong(manifest.getConfig().get("timeout_seconds").toString()) * 1000;
        }
        return DEFAULT_TIMEOUT_MS;
    }

    /**
     * Build a concise catalog of available artifacts for the system prompt.
     * Returns a compact listing grouped by type (Tools, Agents, Pipelines, Skills).
     */
    private String buildArtifactsCatalog(List<FunctionDefinition> functionDefinitions) {
        if (functionDefinitions == null || functionDefinitions.isEmpty()) {
            return "No artifacts available.";
        }
        
        // Group artifacts by type based on naming convention and description markers
        List<String> tools = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        List<String> pipelines = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        
        for (FunctionDefinition func : functionDefinitions) {
            String name = func.getName();
            String desc = func.getDescription() != null ? func.getDescription() : "";
            
            // Categorize based on description markers (added by ArtifactToFunctionConverter)
            if (desc.contains("[AGENT")) {
                agents.add(name);
            } else if (desc.contains("[PIPELINE")) {
                pipelines.add(name);
            } else if (desc.contains("[SKILL")) {
                skills.add(name);
            } else {
                // Default to tool
                tools.add(name);
            }
        }
        
        // Build compact catalog
        StringBuilder catalog = new StringBuilder();
        catalog.append("## Available Artifacts\n\n");
        
        if (!tools.isEmpty()) {
            catalog.append("**Tools** (").append(tools.size()).append("): ");
            catalog.append(String.join(", ", tools));
            catalog.append("\n\n");
        }
        
        if (!agents.isEmpty()) {
            catalog.append("**Agents** (").append(agents.size()).append("): ");
            catalog.append(String.join(", ", agents));
            catalog.append("\n\n");
        }
        
        if (!pipelines.isEmpty()) {
            catalog.append("**Pipelines** (").append(pipelines.size()).append("): ");
            catalog.append(String.join(", ", pipelines));
            catalog.append("\n\n");
        }
        
        if (!skills.isEmpty()) {
            catalog.append("**Skills** (").append(skills.size()).append("): ");
            catalog.append(String.join(", ", skills));
            catalog.append("\n\n");
        }
        
        return catalog.toString().trim();
    }

    /**
     * Create a special "plan_task" function for Phase 1 of ReAct pattern.
     * Agent calls this to declare which artifacts it intends to use.
     */
    private FunctionDefinition createPlanningFunction() {
        return new FunctionDefinition(
            "plan_task",
            "Declare which artifacts you plan to use for this task. Call this FIRST to receive full specifications.",
            createPlanningSchema()
        );
    }

    /**
     * Create JSON schema for the plan_task function.
     * Returns: {reasoning: string, artifacts_to_use: string[]}
     */
    private JsonNode createPlanningSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        // reasoning field
        ObjectNode reasoning = mapper.createObjectNode();
        reasoning.put("type", "string");
        reasoning.put("description", "Explain your thought process about which artifacts to use and why");
        properties.set("reasoning", reasoning);
        
        // artifacts_to_use field
        ObjectNode artifacts = mapper.createObjectNode();
        artifacts.put("type", "array");
        ObjectNode itemsSchema = mapper.createObjectNode();
        itemsSchema.put("type", "string");
        artifacts.set("items", itemsSchema);
        artifacts.put("description", "Array of artifact names you plan to use (e.g., ['rss.fetch', 'csv.write'])");
        properties.set("artifacts_to_use", artifacts);
        
        schema.set("properties", properties);
        
        // Required fields
        ArrayNode required = mapper.createArrayNode();
        required.add("reasoning");
        required.add("artifacts_to_use");
        schema.set("required", required);
        
        return schema;
    }

    /**
     * Extract artifact names from the plan_task function call result.
     * Parses the artifacts_to_use array from the plan JSON.
     */
    private List<String> extractArtifactNames(JsonNode plan) {
        List<String> names = new ArrayList<>();
        
        // Debug logging
        if (plan == null) {
            log.error("extractArtifactNames: plan is NULL");
            return names;
        }
        
        log.debug("extractArtifactNames: plan keys present: {}", 
            plan.isObject() ? StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(plan.fieldNames(), Spliterator.ORDERED), false)
                .collect(java.util.stream.Collectors.joining(", ")) : "not an object");
        
        JsonNode artifactsNode = plan.get("artifacts_to_use");
        
        if (artifactsNode == null) {
            log.warn("extractArtifactNames: 'artifacts_to_use' field not found in plan");
            log.debug("Available fields in plan: {}", plan.fieldNames());
        } else if (!artifactsNode.isArray()) {
            log.warn("extractArtifactNames: 'artifacts_to_use' exists but is not an array: type={}", 
                artifactsNode.getNodeType());
        } else {
            log.debug("extractArtifactNames: 'artifacts_to_use' array has {} elements", artifactsNode.size());
            for (JsonNode node : artifactsNode) {
                if (node.isTextual()) {
                    String artifactName = node.asText();
                    names.add(artifactName);
                    log.debug("  - Extracted artifact: {}", artifactName);
                } else {
                    log.warn("  - Skipping non-text node in artifacts_to_use: {}", node);
                }
            }
        }
        
        // Log the reasoning
        JsonNode reasoningNode = plan.get("reasoning");
        if (reasoningNode != null && reasoningNode.isTextual()) {
            log.info("Agent reasoning: {}", reasoningNode.asText());
        } else if (reasoningNode != null) {
            log.debug("reasoning field present but not textual: type={}", reasoningNode.getNodeType());
        }
        
        log.info("Agent selected {} artifacts: {}", names.size(), names);
        return names;
    }

    /**
     * Build function definitions only for selected artifacts.
     * This is Phase 2 - loading full specifications for chosen artifacts only.
     */
    private List<FunctionDefinition> buildSelectedFunctionDefinitions(List<String> selectedNames) {
        List<FunctionDefinition> definitions = new ArrayList<>();
        
        for (String name : selectedNames) {
            try {
                // Try loading as tool
                ToolManifest tool = repository.loadTool(name);
                if (tool != null) {
                    FunctionDefinition definition = artifactConverter.convertTool(tool);
                    definitions.add(definition);
                    log.debug("Loaded tool specification: {}", name);
                    continue;
                }
                
                // Try loading as agent
                AgentManifest agent = repository.loadAgent(name);
                if (agent != null) {
                    FunctionDefinition definition = artifactConverter.convertAgent(agent);
                    definitions.add(definition);
                    log.debug("Loaded agent specification: {}", name);
                    continue;
                }
                
                // Try loading as pipeline
                PipelineManifest pipeline = repository.loadPipeline(name);
                if (pipeline != null) {
                    FunctionDefinition definition = artifactConverter.convertPipeline(pipeline);
                    definitions.add(definition);
                    log.debug("Loaded pipeline specification: {}", name);
                    continue;
                }
                
                log.warn("Artifact not found: {} (selected but unavailable)", name);
            } catch (Exception e) {
                log.error("Failed to load artifact {}: {}", name, e.getMessage());
            }
        }
        
        log.info("Built {} function definitions for selected artifacts", definitions.size());
        return definitions;
    }
}
