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
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.skill.SkillMetadata;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Unified executor for all agent execution modes.
 *
 * <p>Both {@code "simple"} and {@code "agentic"} modes share a single ReAct loop. The mode
 * controls loop depth and whether a planning phase is performed:</p>
 * <ul>
 *   <li>{@code "simple"} — {@code maxIterations=1}; tools may be called, and if invoked a
 *       final synthesis LLM call (without function definitions) produces a natural-language
 *       answer. Phase 1 (planning) is skipped.</li>
 *   <li>{@code "agentic"} (default) — full ReAct loop; Phase 1 (planning) runs when the
 *       artifact catalog exceeds {@value #PLANNING_THRESHOLD}.</li>
 * </ul>
 *
 * <p>Every assistant message and every tool result is saved atomically to both the local
 * {@code history} list and {@link ConversationMemory} immediately after being produced.</p>
 */
public class AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    // --- Loop constants ---
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final int PLANNING_THRESHOLD = 6;

    // --- Always-required dependencies ---
    private final ModelProviderRegistry modelProviderRegistry;
    private final AgentPromptBuilder promptBuilder;
    private final SchemaValidator schemaValidator;
    private final ObjectMapper mapper;

    // --- Agentic-only dependencies (null when constructed without them) ---
    private final ToolExecutor toolExecutor;
    private final ArtifactRepository repository;
    private final ConversationMemory conversationMemory;
    private final ExecutorRegistry executorRegistry;
    private final ArtifactToFunctionConverter artifactConverter;

    // --- Optional: model router for local-first routing & token tracking ---
    private ModelRouter modelRouter;

    /**
     * Returns the conversation memory store, or null if not configured.
     *
     * @return the conversation memory, or null
     */
    public ConversationMemory getConversationMemory() {
        return conversationMemory;
    }

    /**
     * Sets the model router for local-first LLM routing and token tracking.
     *
     * @param modelRouter the model router instance
     */
    public void setModelRouter(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    /**
     * Full constructor — supports both simple and agentic execution.
     *
     * @param modelProviderRegistry LLM provider registry
     * @param promptBuilder         builds {@link ModelRequest} from an {@link AgentRunContext}
     * @param schemaValidator       validates input/output JSON against schemas
     * @param mapper                Jackson ObjectMapper for JSON processing
     * @param toolExecutor          executes tool artifacts
     * @param repository            loads artifacts from the repo
     * @param conversationMemory    persists conversation history for multi-turn dialogue
     * @param executorRegistry      registry for pipeline and skill executor look-up
     */
    public AgentExecutor(ModelProviderRegistry modelProviderRegistry,
                         AgentPromptBuilder promptBuilder,
                         SchemaValidator schemaValidator,
                         ObjectMapper mapper,
                         ToolExecutor toolExecutor,
                         ArtifactRepository repository,
                         ConversationMemory conversationMemory,
                         ExecutorRegistry executorRegistry) {
        this.modelProviderRegistry = modelProviderRegistry;
        this.promptBuilder = promptBuilder;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
        this.toolExecutor = toolExecutor;
        this.repository = repository;
        this.conversationMemory = conversationMemory;
        this.executorRegistry = executorRegistry;
        this.artifactConverter = new ArtifactToFunctionConverter(mapper);
    }

    /**
     * Convenience constructor for simple-only usage (e.g. unit tests).
     * Agentic execution will throw an informative error if called without agentic dependencies.
     *
     * @param modelProviderRegistry LLM provider registry
     * @param promptBuilder         prompt builder
     * @param schemaValidator       schema validator
     * @param mapper                Jackson ObjectMapper
     */
    public AgentExecutor(ModelProviderRegistry modelProviderRegistry,
                         AgentPromptBuilder promptBuilder,
                         SchemaValidator schemaValidator,
                         ObjectMapper mapper) {
        this(modelProviderRegistry, promptBuilder, schemaValidator, mapper, null, null, null, null);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Execute an agent using a unified ReAct loop.
     *
     * <p>When {@code mode=simple}, {@code maxIterations} is forced to 1. If the model
     * invokes a tool during that single iteration a final synthesis call is made to
     * produce a natural-language answer.</p>
     *
     * @param manifest       agent manifest
     * @param input          input JSON
     * @param conversationId conversation ID for multi-turn dialogue (may be null)
     * @return execution result
     */
    public RunResult execute(AgentManifest manifest, JsonNode input, String conversationId) {
        boolean simpleMode = isSimpleMode(manifest);
        log.debug("Agent '{}' running in {} mode", manifest.getAgent().getName(),
                simpleMode ? "simple" : "agentic");
        return executeReActLoop(manifest, input, conversationId, simpleMode);
    }

    // =========================================================================
    // Mode detection
    // =========================================================================

    /**
     * Returns {@code true} when the manifest declares {@code mode: simple}, or falls back to
     * treating {@code max_iterations == 1} as simple for legacy manifests.
     *
     * @param manifest agent manifest
     * @return true if simple (single-shot) mode
     */
    public boolean isSimpleMode(AgentManifest manifest) {
        if (manifest.getMode() != null) {
            return "simple".equalsIgnoreCase(manifest.getMode().trim());
        }
        Object maxIter = manifest.getConfig() != null ? manifest.getConfig().get("max_iterations") : null;
        return maxIter instanceof Number n && n.intValue() == 1;
    }

    // =========================================================================
    // Unified ReAct loop
    // =========================================================================

    private RunResult executeReActLoop(AgentManifest manifest, JsonNode input,
                                       String conversationId, boolean simpleMode) {
        if (!simpleMode) {
            requireAgenticDependencies();
        }

        long startTime = System.currentTimeMillis();
        ExecutionTrace executionTrace = new ExecutionTrace("agent");

        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString();
        }

        ValidationResult inputValidation = schemaValidator.validate(input, manifest.getInput().getSchema());
        if (!inputValidation.isValid()) {
            return RunResult.failed(String.join(", ", inputValidation.getErrors()));
        }

        // In simple mode there is no persistent memory, so history starts empty.
        List<Message> history = simpleMode
                ? new ArrayList<>()
                : conversationMemory.loadHistory(conversationId);

        List<FunctionDefinition> allFunctionDefinitions = simpleMode
                ? buildFunctionDefinitions(manifest)   // tools are still available in simple mode
                : buildFunctionDefinitions(manifest);

        // Inject system prompt (only when history is empty — i.e. first turn)
        if (history.isEmpty()) {
            injectSystemPrompt(manifest, history, conversationId, allFunctionDefinitions, simpleMode);
        }

        Message userMessage = Message.user(promptBuilder.buildUserContent(manifest, input));
        appendMessage(history, conversationId, userMessage, simpleMode);

        if (log.isTraceEnabled()) {
            logFunctionCatalog(history, allFunctionDefinitions);
        }

        // Simple mode: maxIterations=1; agentic: configured or default.
        int maxIterations = simpleMode ? 1 : getMaxIterations(manifest);
        long timeoutMs = getTimeoutMs(manifest);

        // Planning phase only in agentic mode when artifact count exceeds threshold.
        boolean planningActive = !simpleMode && allFunctionDefinitions.size() > PLANNING_THRESHOLD;
        List<FunctionDefinition> currentFunctions = planningActive
                ? List.of(createPlanningFunction())
                : new ArrayList<>(allFunctionDefinitions);

        if (!planningActive) {
            log.info("Skipping planning phase: {} artifacts (threshold={}), simpleMode={}",
                    allFunctionDefinitions.size(), PLANNING_THRESHOLD, simpleMode);
        }

        List<ToolResultEntry> allToolResults = new ArrayList<>();
        boolean toolCalledInSimpleMode = false;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            long iterationStartTime = System.currentTimeMillis();
            AgentIterationTrace iterationTrace = new AgentIterationTrace(iteration + 1);
            boolean iterationAddedToTrace = false;

            try {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw new IllegalStateException("Agent execution timed out after " + timeoutMs + "ms");
                }

                logIterationHeader(iteration, planningActive, currentFunctions.size(), simpleMode);

                ModelRequest request = buildModelRequest(manifest, history, currentFunctions);

                ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
                long callStart = System.currentTimeMillis();
                ModelResponse response = provider.generate(request);
                long callDuration = System.currentTimeMillis() - callStart;
                log.info("Model response received in {}ms, finish_reason: {}", callDuration, response.getFinishReason());

                // Track token usage
                if (modelRouter != null) {
                    modelRouter.recordUsage(manifest.getModel().getProvider(), response);
                }

                // --- Persist assistant message immediately ---
                Message assistantMessage = buildAssistantMessage(response);
                appendMessage(history, conversationId, assistantMessage, simpleMode);
                iterationTrace.setReasoning(response.getContent());

                // --- Terminal condition: model finished without tool calls ---
                if (isTerminal(response)) {
                    finalizeIterationTrace(iterationTrace, iterationStartTime, true, executionTrace);
                    iterationAddedToTrace = true;
                    return buildFinalResult(response.getContent(), manifest, executionTrace,
                            startTime, conversationId, iteration);
                }

                // --- Handle function calls ---
                if (hasFunctionCalls(response)) {
                    List<FunctionCall> deduplicatedCalls = deduplicateFunctionCalls(response.getFunctionCalls());
                    if (deduplicatedCalls.size() < response.getFunctionCalls().size()) {
                        log.info("Deduplicated {} redundant tool call(s) (model returned {}, keeping {})",
                                response.getFunctionCalls().size() - deduplicatedCalls.size(),
                                response.getFunctionCalls().size(), deduplicatedCalls.size());
                    }

                    for (FunctionCall functionCall : deduplicatedCalls) {

                        // --- Phase 1: plan_task ---
                        if ("plan_task".equals(functionCall.getName())) {
                            currentFunctions = handlePlanTask(functionCall, allFunctionDefinitions,
                                    history, conversationId, iterationTrace, simpleMode);
                            planningActive = false;

                            finalizeIterationTrace(iterationTrace, iterationStartTime, false, executionTrace);
                            iterationAddedToTrace = true;
                            continue;
                        }

                        // --- Phase 2: regular tool/artifact call ---
                        long toolCallStart = System.currentTimeMillis();
                        RunResult toolResult = executeFunctionCall(functionCall);
                        long toolCallDuration = System.currentTimeMillis() - toolCallStart;

                        recordToolCallTrace(iterationTrace, functionCall, toolResult, toolCallDuration);
                        allToolResults.add(new ToolResultEntry(functionCall.getName(), toolResult));
                        if (simpleMode) {
                            toolCalledInSimpleMode = true;
                        }

                        String toolContent = toolResultContent(toolResult);
                        Message toolMessage = Message.tool(functionCall.getId(), functionCall.getName(), toolContent);
                        appendMessage(history, conversationId, toolMessage, simpleMode);

                        // Remove executed function so the model doesn't call it twice
                        String executedName = functionCall.getName();
                        currentFunctions = currentFunctions.stream()
                                .filter(f -> !f.getName().equals(executedName))
                                .collect(Collectors.toList());
                    }

                    if (!iterationAddedToTrace) {
                        // Determine remaining tools and optionally guide the model
                        if (currentFunctions.isEmpty()) {
                            log.info("All tools executed. Continuing loop for model to synthesize final answer.");
                            currentFunctions = new ArrayList<>(allFunctionDefinitions);
                        } else {
                            List<String> remainingNames = currentFunctions.stream()
                                    .map(FunctionDefinition::getName).toList();
                            log.info("Tools called this iteration. {} tools remaining: {}",
                                    currentFunctions.size(), remainingNames);

                            String guidanceMsg = String.format(
                                    "Tool results received. Now call the remaining tool(s): %s with the data above. "
                                    + "Use the tool results from the previous call as input.",
                                    remainingNames);
                            Message guidance = Message.user(guidanceMsg);
                            appendMessage(history, conversationId, guidance, simpleMode);
                        }
                    }
                }

                if (!iterationAddedToTrace) {
                    finalizeIterationTrace(iterationTrace, iterationStartTime, false, executionTrace);
                }

            } catch (Exception e) {
                log.error("Iteration {} failed with exception: {}", iteration + 1, e.getMessage());
                finalizeIterationTrace(iterationTrace, iterationStartTime, false, executionTrace);
                iterationTrace.setReasoning("Iteration failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return buildFailedResult(e.getMessage(), executionTrace, startTime, conversationId, iteration + 1);
            }
        }

        // --- Simple mode post-processing: synthesize tool results ---
        if (simpleMode && toolCalledInSimpleMode) {
            return synthesizeSimpleResult(manifest, history, conversationId, allToolResults,
                    executionTrace, startTime);
        }

        RunResult failedResult = RunResult.failed("Agent exceeded maximum iterations (" + maxIterations + ")");
        failedResult.setExecutionTrace(executionTrace);
        return failedResult;
    }

    // =========================================================================
    // Message helpers (atomic append to history + memory)
    // =========================================================================

    /**
     * Appends a message to the local {@code history} list and, when not in simple mode,
     * persists it atomically to {@link ConversationMemory}.
     *
     * @param history        local conversation history
     * @param conversationId conversation identifier
     * @param message        the message to append
     * @param simpleMode     when {@code true}, memory persistence is skipped
     */
    private void appendMessage(List<Message> history, String conversationId,
                               Message message, boolean simpleMode) {
        history.add(message);
        if (!simpleMode && conversationMemory != null) {
            conversationMemory.saveMessage(conversationId, message);
        }
    }

    private void injectSystemPrompt(AgentManifest manifest, List<Message> history,
                                    String conversationId, List<FunctionDefinition> functionDefinitions,
                                    boolean simpleMode) {
        if (manifest.getInstructions() == null) {
            return;
        }
        String systemPrompt = manifest.getInstructions().getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return;
        }
        if (systemPrompt.contains("{{ARTIFACTS_CATALOG}}")) {
            String catalog = buildArtifactsCatalog(functionDefinitions);
            log.info("Injecting artifacts catalog ({} total artifacts)", functionDefinitions.size());
            log.info("Artifacts catalog:\n{}", catalog);
            systemPrompt = systemPrompt.replace("{{ARTIFACTS_CATALOG}}", catalog);
        }
        log.info("=== SYSTEM PROMPT INJECTED ===");
        log.debug("System prompt length: {} characters", systemPrompt.length());
        if (log.isTraceEnabled()) {
            log.trace("Full system prompt:\n{}", systemPrompt);
        }
        appendMessage(history, conversationId, Message.system(systemPrompt), simpleMode);
    }

    // =========================================================================
    // Result builders
    // =========================================================================

    private RunResult buildFinalResult(String rawContent, AgentManifest manifest,
                                       ExecutionTrace executionTrace, long startTime,
                                       String conversationId, int iteration) {
        try {
            log.info("=== AGENT OUTPUT RECEIVED ===");
            log.info("Raw response content:\n{}", rawContent);

            JsonNode output = mapper.readTree(rawContent);
            log.info("Parsed JSON structure: {}", output.toPrettyString());
            log.info("===========================");

            ValidationResult outputValidation = schemaValidator.validate(output, manifest.getOutput().getSchema());
            if (!outputValidation.isValid()) {
                log.error("=== OUTPUT VALIDATION FAILED ===");
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

    /**
     * Performs a final LLM synthesis call (without function definitions) to convert
     * tool results into a natural-language response in simple mode.
     */
    private RunResult synthesizeSimpleResult(AgentManifest manifest, List<Message> history,
                                             String conversationId, List<ToolResultEntry> toolResults,
                                             ExecutionTrace executionTrace, long startTime) {
        log.info("=== SIMPLE MODE SYNTHESIS: calling LLM to synthesize {} tool result(s) ===",
                toolResults.size());

        ModelRequest synthesisRequest = buildModelRequest(manifest, history, List.of());
        ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
        ModelResponse synthesisResponse = provider.generate(synthesisRequest);

        // Track synthesis token usage
        if (modelRouter != null) {
            modelRouter.recordUsage(manifest.getModel().getProvider(), synthesisResponse);
        }

        Message synthesisMessage = Message.assistant(synthesisResponse.getContent());
        appendMessage(history, conversationId, synthesisMessage, true);

        return buildFinalResult(synthesisResponse.getContent(), manifest, executionTrace,
                startTime, conversationId, 0);
    }

    private RunResult buildFailedResult(String error, ExecutionTrace executionTrace,
                                        long startTime, String conversationId, int iterations) {
        RunResult failedResult = RunResult.failed(error);
        failedResult.setExecutionTrace(executionTrace);
        ExecutionMetadata metadata = new ExecutionMetadata();
        metadata.setStartedAt(startTime);
        metadata.setEndedAt(System.currentTimeMillis());
        metadata.setDetails("conversationId=" + conversationId + ", iterations=" + iterations + ", failed");
        failedResult.setMetadata(metadata);
        return failedResult;
    }

    // =========================================================================
    // Iteration trace helpers
    // =========================================================================

    private void finalizeIterationTrace(AgentIterationTrace iterationTrace, long iterationStartTime,
                                        boolean complete, ExecutionTrace executionTrace) {
        iterationTrace.setDurationMs(System.currentTimeMillis() - iterationStartTime);
        iterationTrace.setComplete(complete);
        executionTrace.addIteration(iterationTrace);
    }

    private void recordToolCallTrace(AgentIterationTrace iterationTrace, FunctionCall functionCall,
                                     RunResult toolResult, long durationMs) {
        ToolCallTrace toolCallTrace = new ToolCallTrace(
                functionCall.getName(),
                functionCall.getArguments(),
                toolResult.getOutput(),
                durationMs,
                toolResult.getStatus() == ExecutionStatus.SUCCESS);
        if (toolResult.getStatus() != ExecutionStatus.SUCCESS) {
            toolCallTrace.setError(toolResult.getError());
        }
        iterationTrace.addToolCall(toolCallTrace);
    }

    // =========================================================================
    // Logging helpers
    // =========================================================================

    private void logIterationHeader(int iteration, boolean planningActive,
                                    int functionCount, boolean simpleMode) {
        if (simpleMode) {
            log.debug("=== Iteration {} (simple mode) ===", iteration + 1);
            return;
        }
        if (iteration == 0 && planningActive) {
            log.info("=== PHASE 1: PLANNING (Iteration {}) ===", iteration + 1);
            log.info("Agent will select artifacts from catalog");
            log.info("Available functions: plan_task ONLY (artifact specs will be loaded after planning)");
        } else if (planningActive) {
            log.info("=== PHASE 2: EXECUTION (Iteration {}) ===", iteration + 1);
            log.info("Available functions: {} selected artifacts", functionCount);
        } else {
            log.debug("=== Iteration {} (continuing execution) ===", iteration + 1);
        }
    }

    private void logFunctionCatalog(List<Message> history, List<FunctionDefinition> functionDefinitions) {
        log.trace("=== AGENT SYSTEM PROMPT ===");
        for (Message msg : history) {
            if ("system".equals(msg.getRole())) log.trace(msg.getContent());
        }
        log.trace("=== AVAILABLE FUNCTIONS ({}) ===", functionDefinitions.size());
        for (FunctionDefinition func : functionDefinitions) {
            log.trace("Function: {} - {}", func.getName(), func.getDescription());
        }
        log.trace("==============================");
    }

    // =========================================================================
    // Model request / response helpers
    // =========================================================================

    private ModelRequest buildModelRequest(AgentManifest manifest, List<Message> history,
                                           List<FunctionDefinition> functions) {
        ModelRequest request = new ModelRequest();
        request.setModel(manifest.getModel().getName());
        request.setTemperature(manifest.getModel().getTemperature());
        request.setThink(manifest.getModel().getThink());
        request.setMessages(new ArrayList<>(history));
        request.setFunctions(functions);

        log.info("Sending {} functions to model:", functions.size());
        for (FunctionDefinition func : functions) {
            log.info("  - {} : {}", func.getName(), func.getDescription());
        }
        log.debug("Calling model provider: {}", manifest.getModel().getProvider());
        return request;
    }

    private Message buildAssistantMessage(ModelResponse response) {
        if (response.getFunctionCalls() != null && !response.getFunctionCalls().isEmpty()) {
            return Message.assistantWithToolCalls(
                    response.getContent() != null ? response.getContent() : "",
                    response.getFunctionCalls());
        }
        return Message.assistant(response.getContent());
    }

    private boolean isTerminal(ModelResponse response) {
        return "stop".equals(response.getFinishReason())
                || response.getFunctionCalls() == null
                || response.getFunctionCalls().isEmpty();
    }

    private boolean hasFunctionCalls(ModelResponse response) {
        return "function_call".equals(response.getFinishReason())
                && response.getFunctionCalls() != null;
    }

    /**
     * Removes duplicate function calls produced by the model in the same response.
     * Two calls are considered duplicates when they share the same name and identical arguments.
     * The first occurrence (by list order) is retained; subsequent duplicates are dropped.
     */
    private List<FunctionCall> deduplicateFunctionCalls(List<FunctionCall> calls) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        List<FunctionCall> unique = new ArrayList<>();
        for (FunctionCall call : calls) {
            String key = call.getName() + "|" + (call.getArguments() != null ? call.getArguments().toString() : "");
            if (seen.add(key)) {
                unique.add(call);
            }
        }
        return unique;
    }

    private String toolResultContent(RunResult toolResult) {
        return toolResult.getStatus() == ExecutionStatus.SUCCESS
                ? toolResult.getOutput().toString()
                : "Error: " + toolResult.getError();
    }

    // =========================================================================
    // Phase 1: plan_task handler
    // =========================================================================

    private List<FunctionDefinition> handlePlanTask(FunctionCall functionCall,
                                                    List<FunctionDefinition> allFunctionDefinitions,
                                                    List<Message> history, String conversationId,
                                                    AgentIterationTrace iterationTrace,
                                                    boolean simpleMode) {
        log.info("=== PHASE 1 COMPLETE: Plan received ===");
        JsonNode plan = functionCall.getArguments();
        log.debug("plan_task arguments received: {}", plan != null ? plan.toString() : "null");
        if (log.isTraceEnabled() && plan != null) {
            log.trace("plan_task full structure: {}", plan.toPrettyString());
        }

        List<String> selectedArtifacts = extractArtifactNames(plan);

        List<FunctionDefinition> nextFunctions;
        if (selectedArtifacts.isEmpty()) {
            log.warn("Agent selected no artifacts - falling back to all available");
            nextFunctions = new ArrayList<>(allFunctionDefinitions);
        } else {
            nextFunctions = buildSelectedFunctionDefinitions(selectedArtifacts);
            if (nextFunctions.isEmpty()) {
                log.warn("None of the selected artifacts were found: {}", selectedArtifacts);
                nextFunctions = new ArrayList<>(allFunctionDefinitions);
            }
        }

        String confirmationMsg = String.format(
                "Planning complete. Selected artifacts: %s. "
                + "Now call the actual artifacts with proper parameters. "
                + "Do NOT call plan_task again.",
                selectedArtifacts);
        Message confirmation = Message.tool(functionCall.getId(), "plan_task", confirmationMsg);
        appendMessage(history, conversationId, confirmation, simpleMode);

        if (plan != null && plan.has("reasoning")) {
            iterationTrace.setReasoning(plan.get("reasoning").asText());
        }

        log.info("=== PHASE 2 READY: {} artifacts loaded ===", nextFunctions.size());
        return nextFunctions;
    }

    // =========================================================================
    // Function call dispatch
    // =========================================================================

    private RunResult executeFunctionCall(FunctionCall functionCall) {
        String artifactName = functionCall.getName();
        JsonNode arguments = functionCall.getArguments();

        try {
            ToolManifest tool = repository.loadTool(artifactName);
            if (tool != null) return toolExecutor.execute(tool, arguments);
        } catch (Exception e) {
            // Not a tool — continue
        }

        try {
            AgentManifest agent = repository.loadAgent(artifactName);
            if (agent != null) {
                // Nested agents always run as a fresh execution
                return execute(agent, arguments, null);
            }
        } catch (Exception e) {
            // Not an agent — continue
        }

        try {
            PipelineManifest pipeline = repository.loadPipeline(artifactName);
            if (pipeline != null) {
                if (executorRegistry != null && executorRegistry.isRegistered(ExecutorRegistry.ExecutorType.PIPELINE)) {
                    return executorRegistry.executePipeline(pipeline, arguments);
                }
                return RunResult.failed("Pipeline execution not available: PipelineExecutor not initialized");
            }
        } catch (Exception e) {
            return RunResult.failed("Artifact execution failed: " + e.getMessage());
        }

        try {
            SkillManifest skill = repository.loadSkill(artifactName);
            if (skill != null) {
                if (executorRegistry != null && executorRegistry.isRegistered(ExecutorRegistry.ExecutorType.SKILL)) {
                    return executorRegistry.executeSkill(skill, arguments);
                }
                return RunResult.failed("Skill execution not available: SkillExecutor not initialized");
            }
        } catch (Exception e) {
            // Fall through
        }

        return RunResult.failed("Artifact not found: " + artifactName);
    }

    // =========================================================================
    // Function definition building
    // =========================================================================

    private List<FunctionDefinition> buildFunctionDefinitions(AgentManifest manifest) {
        List<FunctionDefinition> definitions = new ArrayList<>();
        for (String artifactName : getConfiguredArtifactNames(manifest)) {
            FunctionDefinition definition = resolveFunctionDefinition(artifactName);
            if (definition != null) {
                definitions.add(definition);
            } else {
                log.debug("Artifact '{}' not found as tool, agent, pipeline, or skill - skipping", artifactName);
            }
        }
        return definitions;
    }

    private List<FunctionDefinition> buildSelectedFunctionDefinitions(List<String> selectedNames) {
        List<FunctionDefinition> definitions = new ArrayList<>();
        for (String name : selectedNames) {
            try {
                FunctionDefinition definition = resolveFunctionDefinition(name);
                if (definition != null) {
                    definitions.add(definition);
                    log.debug("Loaded artifact specification: {}", name);
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

    private FunctionDefinition resolveFunctionDefinition(String artifactName) {
        try {
            ToolManifest tool = repository.loadTool(artifactName);
            if (tool != null) return artifactConverter.convertTool(tool);
        } catch (Exception e) {
            // Not a tool
        }
        try {
            AgentManifest agent = repository.loadAgent(artifactName);
            if (agent != null) return artifactConverter.convertAgent(agent);
        } catch (Exception e) {
            // Not an agent
        }
        try {
            PipelineManifest pipeline = repository.loadPipeline(artifactName);
            if (pipeline != null) return artifactConverter.convertPipeline(pipeline);
        } catch (Exception e) {
            // Not a pipeline
        }
        try {
            SkillMetadata skill = repository.getSkillMetadata(artifactName);
            if (skill != null) return artifactConverter.convertSkill(skill);
        } catch (Exception e) {
            // Not a skill
        }
        return null;
    }

    private List<String> getConfiguredArtifactNames(AgentManifest manifest) {
        List<String> artifactNames = new ArrayList<>();

        Object toolsConfig = manifest.getConfig() != null ? manifest.getConfig().get("tools") : null;
        if (toolsConfig instanceof List<?> configuredTools) {
            configuredTools.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(artifactNames::add);
        }

        Object skillsConfig = manifest.getConfig() != null ? manifest.getConfig().get("skills") : null;
        if (skillsConfig instanceof List<?> configuredSkills) {
            configuredSkills.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(name -> {
                        if (!artifactNames.contains(name)) artifactNames.add(name);
                    });
        }

        if (artifactNames.isEmpty() && manifest.getTools() != null) {
            for (String toolName : manifest.getTools()) {
                if ("*".equals(toolName)) {
                    repository.listTools().stream()
                            .filter(name -> !artifactNames.contains(name))
                            .forEach(artifactNames::add);
                } else if (!artifactNames.contains(toolName)) {
                    artifactNames.add(toolName);
                }
            }
        }

        return artifactNames;
    }

    // =========================================================================
    // Planning (Phase 1) helpers
    // =========================================================================

    /**
     * Creates the special {@code plan_task} function used in Phase 1.
     * The agent calls this to declare which artifacts it intends to use.
     */
    private FunctionDefinition createPlanningFunction() {
        return FunctionDefinition.builder()
                .name("plan_task")
                .description("Declare which artifacts you plan to use for this task. Call this FIRST to receive full specifications.")
                .parameters(createPlanningSchema())
                .build();
    }

    private JsonNode createPlanningSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode reasoning = mapper.createObjectNode();
        reasoning.put("type", "string");
        reasoning.put("description", "Explain your thought process about which artifacts to use and why");
        properties.set("reasoning", reasoning);

        ObjectNode artifacts = mapper.createObjectNode();
        artifacts.put("type", "array");
        ObjectNode itemsSchema = mapper.createObjectNode();
        itemsSchema.put("type", "string");
        artifacts.set("items", itemsSchema);
        artifacts.put("description", "Array of artifact names you plan to use. "
                + "Prefer a single composite tool over multiple individual tools when available "
                + "(e.g., use 'rss.csv' instead of separate 'rss.fetch' + 'csv.write')");
        properties.set("artifacts_to_use", artifacts);

        schema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("reasoning");
        required.add("artifacts_to_use");
        schema.set("required", required);

        return schema;
    }

    private List<String> extractArtifactNames(JsonNode plan) {
        List<String> names = new ArrayList<>();

        if (plan == null) {
            log.error("extractArtifactNames: plan is NULL");
            return names;
        }

        log.debug("extractArtifactNames: plan keys present: {}",
                plan.isObject() ? StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(plan.fieldNames(), Spliterator.ORDERED), false)
                        .collect(Collectors.joining(", ")) : "not an object");

        JsonNode artifactsNode = plan.get("artifacts_to_use");

        if (artifactsNode == null) {
            log.warn("extractArtifactNames: 'artifacts_to_use' field not found in plan");
        } else if (!artifactsNode.isArray()) {
            log.warn("extractArtifactNames: 'artifacts_to_use' is not an array: type={}", artifactsNode.getNodeType());
        } else {
            log.debug("extractArtifactNames: 'artifacts_to_use' array has {} elements", artifactsNode.size());
            for (JsonNode node : artifactsNode) {
                if (node.isTextual()) {
                    names.add(node.asText());
                    log.debug("  - Extracted artifact: {}", node.asText());
                } else {
                    log.warn("  - Skipping non-text node in artifacts_to_use: {}", node);
                }
            }
        }

        JsonNode reasoningNode = plan.get("reasoning");
        if (reasoningNode != null && reasoningNode.isTextual()) {
            log.info("Agent reasoning: {}", reasoningNode.asText());
        }

        log.info("Agent selected {} artifacts: {}", names.size(), names);
        return names;
    }

    // =========================================================================
    // Artifacts catalog (Phase 1 system prompt injection)
    // =========================================================================

    /**
     * Builds a concise markdown catalog of all available artifacts for injection into the
     * system prompt via the {@code {{ARTIFACTS_CATALOG}}} placeholder.
     *
     * @param functionDefinitions full list of available function definitions
     * @return markdown-formatted catalog string
     */
    private String buildArtifactsCatalog(List<FunctionDefinition> functionDefinitions) {
        if (functionDefinitions == null || functionDefinitions.isEmpty()) {
            return "No artifacts available.";
        }

        List<FunctionDefinition> tools = new ArrayList<>();
        List<FunctionDefinition> agents = new ArrayList<>();
        List<FunctionDefinition> skills = new ArrayList<>();

        for (FunctionDefinition func : functionDefinitions) {
            String desc = func.getDescription() != null ? func.getDescription() : "";
            if (desc.contains("[AGENT")) {
                agents.add(func);
            } else if (desc.contains("[SKILL")) {
                skills.add(func);
            } else {
                tools.add(func);
            }
        }

        StringBuilder catalog = new StringBuilder();
        catalog.append("## Available Artifacts\n\n");
        catalog.append("**IMPORTANT**: Prefer tools that combine multiple steps into a single call over calling individual tools separately.\n\n");

        if (!tools.isEmpty()) {
            catalog.append("**Tools** (").append(tools.size()).append("):\n");
            for (FunctionDefinition func : tools) {
                catalog.append("- `").append(func.getName()).append("`: ");
                catalog.append(truncateDescription(func.getDescription())).append("\n");
            }
            catalog.append("\n");
        }

        if (!agents.isEmpty()) {
            catalog.append("**Agents** (").append(agents.size()).append("):\n");
            for (FunctionDefinition func : agents) {
                catalog.append("- `").append(func.getName()).append("`: ");
                catalog.append(truncateDescription(func.getDescription())).append("\n");
            }
            catalog.append("\n");
        }

        if (!skills.isEmpty()) {
            catalog.append("**Skills** (").append(skills.size()).append("):\n");
            for (FunctionDefinition func : skills) {
                catalog.append("- `").append(func.getName()).append("`: ");
                catalog.append(truncateDescription(func.getDescription())).append("\n");
            }
            catalog.append("\n");
        }

        return catalog.toString().trim();
    }

    private String truncateDescription(String description) {
        if (description == null || description.isEmpty()) return "(no description)";
        String clean = description.replaceAll("\\s*\\[(?:AGENT|SKILL)[^]]*\\]", "").trim();
        return clean.length() > 120 ? clean.substring(0, 117) + "..." : clean;
    }

    // =========================================================================
    // Config helpers
    // =========================================================================

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

    private void requireAgenticDependencies() {
        if (toolExecutor == null || repository == null || conversationMemory == null) {
            throw new IllegalStateException(
                    "AgentExecutor was constructed without agentic dependencies "
                    + "(toolExecutor, repository, conversationMemory). "
                    + "Use the full 8-argument constructor for agentic execution.");
        }
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    /** Holds a tool execution result paired with its tool name. */
    private record ToolResultEntry(String toolName, RunResult result) {}
}
