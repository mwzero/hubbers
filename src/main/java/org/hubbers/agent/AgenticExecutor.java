package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

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
        
        // Add system prompt if first message
        if (history.isEmpty() && manifest.getInstructions() != null) {
            String systemPrompt = manifest.getInstructions().getSystemPrompt();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                history.add(Message.system(systemPrompt));
            }
        }
        
        // Add user input
        String userContent = input.toString();
        Message userMessage = Message.user(userContent);
        history.add(userMessage);
        conversationMemory.saveMessage(conversationId, userMessage);
        
        // Prepare function definitions from available tools
        List<FunctionDefinition> functionDefinitions = buildFunctionDefinitions(manifest);
        
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
        
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return RunResult.failed("Agent execution timed out after " + timeoutMs + "ms");
            }
            
            // Build model request with conversation history and available functions
            ModelRequest request = new ModelRequest();
            request.setModel(manifest.getModel().getName());
            request.setTemperature(manifest.getModel().getTemperature());
            request.setMessages(new ArrayList<>(history));
            request.setFunctions(functionDefinitions);
            
            // Call LLM
            ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
            ModelResponse response = provider.generate(request);
            
            // Add assistant response to history
            Message assistantMessage = Message.assistant(response.getContent());
            history.add(assistantMessage);
            conversationMemory.saveMessage(conversationId, assistantMessage);
            
            // Check finish reason
            if ("stop".equals(response.getFinishReason()) || response.getFunctionCalls() == null || response.getFunctionCalls().isEmpty()) {
                // Agent completed - parse final output
                try {
                    JsonNode output = mapper.readTree(response.getContent());
                    ValidationResult outputValidation = schemaValidator.validate(output, manifest.getOutput().getSchema());
                    if (!outputValidation.isValid()) {
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
}
