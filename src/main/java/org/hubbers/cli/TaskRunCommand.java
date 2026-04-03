package org.hubbers.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.nlp.NaturalLanguageTaskService;
import org.hubbers.nlp.TaskExecutionResult;
import org.hubbers.util.JacksonFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI command for executing natural language tasks.
 * Example: hubbers task run "check my Amazon shopping cart" --verbose
 */
@Command(
    name = "run",
    description = "Execute a task described in natural language using available tools"
)
public class TaskRunCommand implements Callable<Integer> {
    
    @Option(names = {"-r", "--request"}, 
            description = "Natural language task request",
            required = true)
    private String request;
    
    @Option(names = {"-c", "--context"}, 
            description = "Context data as JSON string or file path")
    private String context;
    
    @Option(names = {"--conversation"}, 
            description = "Continue existing conversation ID")
    private String conversationId;
    
    @Option(names = {"-v", "--verbose"}, 
            description = "Show detailed agent reasoning and tool calls")
    private boolean verbose;
    
    private final NaturalLanguageTaskService taskService;
    private final ObjectMapper mapper;
    
    public TaskRunCommand(NaturalLanguageTaskService taskService) {
        this.taskService = taskService;
        this.mapper = JacksonFactory.jsonMapper();
    }
    
    @Override
    public Integer call() throws Exception {
        System.out.println("🤖 Executing task: " + request);
        System.out.println();
        
        // Parse context if provided
        JsonNode contextNode = null;
        if (context != null) {
            try {
                contextNode = mapper.readTree(context);
            } catch (Exception e) {
                System.err.println("❌ Invalid context JSON: " + e.getMessage());
                return 1;
            }
        }
        
        // Show progress indicator
        System.out.println("⏳ Agent is thinking and calling tools...");
        System.out.println();
        
        // Execute task
        TaskExecutionResult result;
        if (conversationId != null && !conversationId.isEmpty()) {
            result = taskService.executeTaskWithConversation(request, contextNode, conversationId);
        } else {
            result = taskService.executeTask(request, contextNode);
        }
        
        // Display results
        if (!result.isSuccess()) {
            System.err.println("❌ Task failed: " + result.getError());
            return 1;
        }
        
        System.out.println("✅ Task completed successfully!");
        System.out.println();
        
        // Show tool usage
        if (result.getToolsUsed() != null && !result.getToolsUsed().isEmpty()) {
            System.out.println("🔧 Tools used: " + String.join(", ", result.getToolsUsed()));
            System.out.println("📊 Iterations: " + result.getIterations());
            System.out.println();
        }
        
        // Show reasoning in verbose mode
        if (verbose && result.getReasoning() != null && !result.getReasoning().isEmpty()) {
            System.out.println("💭 Agent reasoning:");
            System.out.println(result.getReasoning());
            System.out.println();
        }
        
        // Show result
        System.out.println("📋 Result:");
        String resultJson = mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(result.getResult());
        System.out.println(resultJson);
        System.out.println();
        
        // Show conversation ID for follow-ups
        if (result.getConversationId() != null) {
            System.out.println("💬 Conversation ID: " + result.getConversationId());
            System.out.println("   Use --conversation " + result.getConversationId() + 
                " for follow-up requests");
        }
        
        return 0;
    }
}