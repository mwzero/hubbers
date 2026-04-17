package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.agent.memory.ConversationMemory;
import org.hubbers.app.ArtifactRepository;
import org.hubbers.execution.*;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.*;

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
    private final ExecutorRegistry executorRegistry;
    private final AgentExecutor agentExecutor;
    
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final long DEFAULT_TIMEOUT_MS = 60000;
    private static final int PLANNING_THRESHOLD = 6;

    public AgenticExecutor(ModelProviderRegistry modelProviderRegistry,
                          ToolExecutor toolExecutor,
                          ArtifactRepository repository,
                          SchemaValidator schemaValidator,
                          ConversationMemory conversationMemory,
                          ExecutorRegistry executorRegistry,
                          AgentExecutor agentExecutor,
                          ObjectMapper mapper) {
        this.modelProviderRegistry = modelProviderRegistry;
        this.toolExecutor = toolExecutor;
        this.repository = repository;
        this.schemaValidator = schemaValidator;
        this.conversationMemory = conversationMemory;
        this.executorRegistry = executorRegistry;
        this.agentExecutor = agentExecutor;
        this.mapper = mapper;
        this.artifactConverter = new ArtifactToFunctionConverter(mapper);
    }

    /**
     * Execute an agentic agent with optional conversation ID for multi-turn.
     */
    public RunResult execute(AgentManifest manifest, JsonNode input, String conversationId) {
        long startTime = System.currentTimeMillis();
        
        // Create execution trace for agent
        ExecutionTrace executionTrace = new ExecutionTrace("agent");
        
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
                    log.info("Injecting artifacts catalog ({} total artifacts)", functionDefinitions.size());
                    log.info("Artifacts catalog:\n{}", artifactsCatalog);
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
        // Skip planning phase when few artifacts available (saves one LLM round-trip)
        boolean planningPhase = functionDefinitions.size() > PLANNING_THRESHOLD;
        List<FunctionDefinition> currentFunctions = new ArrayList<>(functionDefinitions);
        List<ToolResultEntry> allToolResults = new ArrayList<>();
        
        if (!planningPhase) {
            log.info("Skipping planning phase: only {} artifacts available (threshold={})", 
                functionDefinitions.size(), PLANNING_THRESHOLD);
        }
        
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            long iterationStartTime = System.currentTimeMillis();
            
            // Create iteration trace
            AgentIterationTrace iterationTrace = new AgentIterationTrace(iteration + 1);
            boolean iterationAddedToTrace = false;
            
            try {
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw new IllegalStateException("Agent execution timed out after " + timeoutMs + "ms");
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
            log.info("Sending {} functions to model:", currentFunctions.size());
            for (FunctionDefinition func : currentFunctions) {
                log.info("  - {} : {}", func.getName(), func.getDescription());
            }
            
            // Call LLM
            log.debug("Calling model provider: {}", manifest.getModel().getProvider());
            ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
            long callStart = System.currentTimeMillis();
            ModelResponse response = provider.generate(request);
            long callDuration = System.currentTimeMillis() - callStart;
            log.info("Model response received in {}ms, finish_reason: {}", callDuration, response.getFinishReason());
            
            // Add assistant response to history (include tool_calls if present for proper conversation flow)
            Message assistantMessage;
            if (response.getFunctionCalls() != null && !response.getFunctionCalls().isEmpty()) {
                assistantMessage = Message.assistantWithToolCalls(
                    response.getContent() != null ? response.getContent() : "", 
                    response.getFunctionCalls()
                );
            } else {
                assistantMessage = Message.assistant(response.getContent());
            }
            history.add(assistantMessage);
            conversationMemory.saveMessage(conversationId, assistantMessage);
            
            // Capture reasoning in iteration trace
            iterationTrace.setReasoning(response.getContent());
            
            // Check finish reason
            if ("stop".equals(response.getFinishReason()) || response.getFunctionCalls() == null || response.getFunctionCalls().isEmpty()) {
                // Agent completed - parse final output
                long iterationEndTime = System.currentTimeMillis();
                iterationTrace.setDurationMs(iterationEndTime - iterationStartTime);
                iterationTrace.setComplete(true);
                executionTrace.addIteration(iterationTrace);
                iterationAddedToTrace = true;
                
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
                    result.setExecutionTrace(executionTrace);
                    return result;
                } catch (Exception e) {
                    return RunResult.failed("Failed to parse agent output: " + e.getMessage());
                }
            }
            
            // Execute function calls (tool invocations)
            if ("function_call".equals(response.getFinishReason()) && response.getFunctionCalls() != null) {
                boolean toolsExecuted = false; // Track if real tools (not plan_task) were called
                
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
                            currentFunctions = new ArrayList<>(functionDefinitions);
                        } else {
                            currentFunctions = buildSelectedFunctionDefinitions(selectedArtifacts);
                            
                            if (currentFunctions.isEmpty()) {
                                return RunResult.failed("None of the selected artifacts were found: " + selectedArtifacts);
                            }
                        }
                        
                        // Transition to execution phase
                        planningPhase = false;
                        
                        // Add tool response for plan_task (proper conversation flow for Ollama/OpenAI)
                        String confirmationMsg = String.format(
                            "Planning complete. Selected artifacts: %s. " +
                            "Now call the actual artifacts with proper parameters. " +
                            "Do NOT call plan_task again.",
                            selectedArtifacts
                        );
                        Message confirmation = Message.tool(functionCall.getId(), "plan_task", confirmationMsg);
                        history.add(confirmation);
                        conversationMemory.saveMessage(conversationId, confirmation);
                        
                        log.info("=== PHASE 2 READY: {} artifacts loaded ===", currentFunctions.size());
                        
                        // Add Phase 1 iteration to trace before starting Phase 2
                        long iterationEndTime = System.currentTimeMillis();
                        iterationTrace.setDurationMs(iterationEndTime - iterationStartTime);
                        iterationTrace.setComplete(false); // Not the final iteration, continuing to Phase 2
                        
                        // Extract reasoning from plan
                        if (plan != null && plan.has("reasoning")) {
                            iterationTrace.setReasoning(plan.get("reasoning").asText());
                        }
                        
                        executionTrace.addIteration(iterationTrace);
                        iterationAddedToTrace = true;
                        
                        continue; // Next iteration with full specs
                    }
                    
                    // Regular artifact execution - track tool call
                    toolsExecuted = true;
                    long toolCallStart = System.currentTimeMillis();
                    RunResult toolResult = executeFunctionCall(functionCall);
                    long toolCallDuration = System.currentTimeMillis() - toolCallStart;
                    
                    // Create tool call trace
                    ToolCallTrace toolCallTrace = new ToolCallTrace(
                        functionCall.getName(),
                        functionCall.getArguments(),
                        toolResult.getOutput(),
                        toolCallDuration,
                        toolResult.getStatus() == ExecutionStatus.SUCCESS
                    );
                    if (toolResult.getStatus() != ExecutionStatus.SUCCESS) {
                        toolCallTrace.setError(toolResult.getError());
                    }
                    iterationTrace.addToolCall(toolCallTrace);
                    
                    // Collect tool results for potential direct return
                    allToolResults.add(new ToolResultEntry(functionCall.getName(), toolResult));
                    
                    // Add tool result to history (for multi-step agents)
                    String toolContent = (toolResult.getStatus() == ExecutionStatus.SUCCESS) ? 
                        toolResult.getOutput().toString() : 
                        "Error: " + toolResult.getError();
                    
                    Message toolMessage = Message.tool(functionCall.getId(), functionCall.getName(), toolContent);
                    history.add(toolMessage);
                    conversationMemory.saveMessage(conversationId, toolMessage);
                    
                    // Remove executed tool from available functions (prevent re-calling)
                    String executedName = functionCall.getName();
                    currentFunctions = currentFunctions.stream()
                        .filter(f -> !f.getName().equals(executedName))
                        .collect(java.util.stream.Collectors.toList());
                }
                
                // After executing real tools, decide: continue or return
                if (toolsExecuted) {
                    if (currentFunctions.isEmpty()) {
                        // All planned tools have been called — return results directly
                        log.info("All planned tools executed. Returning results directly.");
                        
                        long iterationEndTime = System.currentTimeMillis();
                        iterationTrace.setDurationMs(iterationEndTime - iterationStartTime);
                        iterationTrace.setComplete(true);
                        executionTrace.addIteration(iterationTrace);
                        iterationAddedToTrace = true;
                        
                        return buildDirectReturn(allToolResults, executionTrace, startTime, conversationId, iteration);
                    }
                    
                    // Tools remain — add guidance and continue loop
                    List<String> remainingNames = currentFunctions.stream()
                        .map(FunctionDefinition::getName).toList();
                    log.info("Tools called this iteration. {} tools remaining: {}", 
                        currentFunctions.size(), remainingNames);
                    
                    String guidanceMsg = String.format(
                        "Tool results received. Now call the remaining tool(s): %s with the data above. " +
                        "Use the tool results from the previous call as input.",
                        remainingNames
                    );
                    Message guidance = Message.user(guidanceMsg);
                    history.add(guidance);
                    conversationMemory.saveMessage(conversationId, guidance);
                }
            }
            
            // Complete iteration trace (only if not already added above)
            if (!iterationAddedToTrace) {
                long iterationEndTime = System.currentTimeMillis();
                iterationTrace.setDurationMs(iterationEndTime - iterationStartTime);
                iterationTrace.setComplete(false); // Not final iteration if we continue
                executionTrace.addIteration(iterationTrace);
            }
            
            } catch (Exception e) {
                // Iteration failed - capture partial trace with error
                log.error("Iteration {} failed with exception: {}", iteration + 1, e.getMessage());
                long iterationEndTime = System.currentTimeMillis();
                iterationTrace.setDurationMs(iterationEndTime - iterationStartTime);
                iterationTrace.setComplete(false);
                String errorReasoning = "Iteration failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                iterationTrace.setReasoning(errorReasoning);
                executionTrace.addIteration(iterationTrace);
                
                // Return failed result with trace
                RunResult failedResult = RunResult.failed(e.getMessage());
                failedResult.setExecutionTrace(executionTrace);
                ExecutionMetadata metadata = new ExecutionMetadata();
                metadata.setStartedAt(startTime);
                metadata.setEndedAt(iterationEndTime);
                metadata.setDetails("conversationId=" + conversationId + ", iterations=" + (iteration + 1) + ", failed");
                failedResult.setMetadata(metadata);
                return failedResult;
            }
        }
        
        RunResult failedResult = RunResult.failed("Agent exceeded maximum iterations (" + maxIterations + ")");
        failedResult.setExecutionTrace(executionTrace);
        return failedResult;
    }

    private RunResult executeFunctionCall(FunctionCall functionCall) {
        String artifactName = functionCall.getName();
        JsonNode arguments = functionCall.getArguments();
        
        // Try loading as tool first
        try {
            ToolManifest tool = repository.loadTool(artifactName);
            if (tool != null) {
                return toolExecutor.execute(tool, arguments);
            }
        } catch (Exception e) {
            // Not a tool, try next type
        }
        
        // Try loading as agent
        try {
            AgentManifest agent = repository.loadAgent(artifactName);
            if (agent != null) {
                if (agentExecutor != null) {
                    return agentExecutor.execute(agent, arguments);
                } else {
                    return RunResult.failed("Agent execution not available: AgentExecutor not initialized");
                }
            }
        } catch (Exception e) {
            // Not an agent, try next type
        }
        
        // Try loading as pipeline
        try {
            PipelineManifest pipeline = repository.loadPipeline(artifactName);
            if (pipeline != null) {
                if (executorRegistry != null && executorRegistry.isRegistered(ExecutorRegistry.ExecutorType.PIPELINE)) {
                    return executorRegistry.executePipeline(pipeline, arguments);
                } else {
                    return RunResult.failed("Pipeline execution not available: PipelineExecutor not initialized");
                }
            }
        } catch (Exception e) {
            return RunResult.failed("Artifact execution failed: " + e.getMessage());
        }
        
        return RunResult.failed("Artifact not found: " + artifactName);
    }

    private List<FunctionDefinition> buildFunctionDefinitions(AgentManifest manifest) {
        List<FunctionDefinition> definitions = new ArrayList<>();
        
        // Check if agent specifies artifacts (tools/agents/pipelines) in config
        Object toolsConfig = manifest.getConfig() != null ? manifest.getConfig().get("tools") : null;
        if (toolsConfig instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> artifactNames = (List<String>) toolsConfig;
            
            for (String artifactName : artifactNames) {
                // Try loading as tool first
                try {
                    ToolManifest tool = repository.loadTool(artifactName);
                    if (tool != null) {
                        definitions.add(artifactConverter.convertTool(tool));
                        continue;
                    }
                } catch (Exception e) {
                    // Not a tool, try next type
                }
                
                // Try loading as agent
                try {
                    AgentManifest agent = repository.loadAgent(artifactName);
                    if (agent != null) {
                        definitions.add(artifactConverter.convertAgent(agent));
                        continue;
                    }
                } catch (Exception e) {
                    // Not an agent, try next type
                }
                
                // Try loading as pipeline
                try {
                    PipelineManifest pipeline = repository.loadPipeline(artifactName);
                    if (pipeline != null) {
                        definitions.add(artifactConverter.convertPipeline(pipeline));
                        continue;
                    }
                } catch (Exception e) {
                    // Not a pipeline either
                }
                
                log.debug("Artifact '{}' not found as tool, agent, or pipeline - skipping", artifactName);
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
     * Pipelines are presented as tools (composite tools) since from the agent's
     * perspective they are just callable functions that perform complex tasks.
     * Includes brief descriptions so the model can make informed planning decisions.
     */
    private String buildArtifactsCatalog(List<FunctionDefinition> functionDefinitions) {
        if (functionDefinitions == null || functionDefinitions.isEmpty()) {
            return "No artifacts available.";
        }
        
        // Group artifacts by type — pipelines are treated as tools
        List<FunctionDefinition> tools = new ArrayList<>();
        List<FunctionDefinition> agents = new ArrayList<>();
        List<FunctionDefinition> skills = new ArrayList<>();
        
        for (FunctionDefinition func : functionDefinitions) {
            String desc = func.getDescription() != null ? func.getDescription() : "";
            
            // Categorize based on description markers (added by ArtifactToFunctionConverter)
            if (desc.contains("[AGENT")) {
                agents.add(func);
            } else if (desc.contains("[SKILL")) {
                skills.add(func);
            } else {
                // Tools and pipelines are both "tools" from the agent's perspective
                tools.add(func);
            }
        }
        
        // Build catalog with names and descriptions
        StringBuilder catalog = new StringBuilder();
        catalog.append("## Available Artifacts\n\n");
        catalog.append("**IMPORTANT**: Prefer tools that combine multiple steps into a single call over calling individual tools separately.\n\n");
        
        if (!tools.isEmpty()) {
            catalog.append("**Tools** (").append(tools.size()).append("):\n");
            for (FunctionDefinition func : tools) {
                catalog.append("- `").append(func.getName()).append("`: ");
                catalog.append(truncateDescription(func.getDescription()));
                catalog.append("\n");
            }
            catalog.append("\n");
        }
        
        if (!agents.isEmpty()) {
            catalog.append("**Agents** (").append(agents.size()).append("):\n");
            for (FunctionDefinition func : agents) {
                catalog.append("- `").append(func.getName()).append("`: ");
                catalog.append(truncateDescription(func.getDescription()));
                catalog.append("\n");
            }
            catalog.append("\n");
        }
        
        if (!skills.isEmpty()) {
            catalog.append("**Skills** (").append(skills.size()).append("):\n");
            for (FunctionDefinition func : skills) {
                catalog.append("- `").append(func.getName()).append("`: ");
                catalog.append(truncateDescription(func.getDescription()));
                catalog.append("\n");
            }
            catalog.append("\n");
        }
        
        return catalog.toString().trim();
    }

    /**
     * Truncate a description to a reasonable length for catalog display.
     */
    private String truncateDescription(String description) {
        if (description == null || description.isEmpty()) {
            return "(no description)";
        }
        // Remove bracket markers like [AGENT ...] for cleaner catalog display
        String clean = description.replaceAll("\\s*\\[(?:AGENT|SKILL)[^]]*\\]", "").trim();
        if (clean.length() > 120) {
            return clean.substring(0, 117) + "...";
        }
        return clean;
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
        artifacts.put("description", "Array of artifact names you plan to use. " +
            "Prefer a single composite tool over multiple individual tools when available " +
            "(e.g., use 'rss.csv' instead of separate 'rss.fetch' + 'csv.write')");
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

    /**
     * Build a direct RunResult from collected tool results, bypassing an extra LLM synthesis call.
     * For single-tool results, returns the tool output directly.
     * For multi-tool results, returns a combined JSON object with all tool outputs.
     *
     * @param toolResults collected tool execution results
     * @param executionTrace the execution trace for metadata
     * @param startTime execution start timestamp
     * @param conversationId the conversation ID
     * @param iteration the current iteration number
     * @return RunResult with combined tool outputs
     */
    private RunResult buildDirectReturn(List<ToolResultEntry> toolResults, ExecutionTrace executionTrace,
                                         long startTime, String conversationId, int iteration) {
        JsonNode output;
        if (toolResults.size() == 1) {
            ToolResultEntry single = toolResults.get(0);
            if (single.result().getStatus() == ExecutionStatus.SUCCESS) {
                output = single.result().getOutput();
            } else {
                RunResult failed = RunResult.failed("Tool " + single.toolName() + " failed: " + single.result().getError());
                failed.setExecutionTrace(executionTrace);
                return failed;
            }
        } else {
            ObjectNode combined = mapper.createObjectNode();
            ArrayNode results = mapper.createArrayNode();
            for (ToolResultEntry entry : toolResults) {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("tool", entry.toolName());
                toolNode.put("status", entry.result().getStatus().toString());
                if (entry.result().getStatus() == ExecutionStatus.SUCCESS) {
                    toolNode.set("output", entry.result().getOutput());
                } else {
                    toolNode.put("error", entry.result().getError());
                }
                results.add(toolNode);
            }
            combined.set("results", results);
            combined.put("tools_used", toolResults.size());
            output = combined;
        }

        RunResult result = RunResult.success(output);
        ExecutionMetadata metadata = new ExecutionMetadata();
        metadata.setStartedAt(startTime);
        metadata.setEndedAt(System.currentTimeMillis());
        metadata.setDetails("conversationId=" + conversationId + ", iterations=" + (iteration + 1) + ", directReturn=true");
        result.setMetadata(metadata);
        result.setExecutionTrace(executionTrace);
        return result;
    }

    /**
     * Holds a tool execution result paired with its tool name.
     */
    private record ToolResultEntry(String toolName, RunResult result) {}
}
