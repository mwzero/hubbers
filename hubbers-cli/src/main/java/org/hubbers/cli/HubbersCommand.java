package org.hubbers.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.hubbers.app.RuntimeFacade;
import org.hubbers.config.ConfigLoader;
import org.hubbers.config.RepoPathResolver;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.forms.FormTrigger;
import org.hubbers.mcp.McpPromptProvider;
import org.hubbers.mcp.McpRequestHandler;
import org.hubbers.mcp.McpToolProvider;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.web.ManifestFileService;
import org.hubbers.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;


@CommandLine.Command(
    name = "hubbers",
    description = "Hubbers CLI - Execute agents, tools, pipelines, and skills",
    subcommands = {
        HubbersCommand.ListCommand.class,
        HubbersCommand.AgentCommand.class,
        HubbersCommand.ToolCommand.class,
        HubbersCommand.PipelineCommand.class,
        HubbersCommand.SkillCommand.class,
        HubbersCommand.WebCommand.class,
        McpCommand.class
    }
)
public class HubbersCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(HubbersCommand.class);

    @CommandLine.Option(
        names = {"-h", "--help"},
        usageHelp = true,
        description = "Mostra questo messaggio di aiuto"
    )
    boolean helpRequested;

    @CommandLine.Option(
        names = {"-V", "--version"},
        versionHelp = true,
        description = "Mostra la versione"
    )
    boolean versionRequested;

    @CommandLine.Option(
        names = "--repo",
        description = "Path to the repository folder (default: hubbers-repo/src/main/resources/repo)"
    )
    String repoPath = RepoPathResolver.DEFAULT_REPO_PATH;

    final RuntimeFacade runtimeFacade;

    public HubbersCommand(RuntimeFacade runtimeFacade) {
        this.runtimeFacade = runtimeFacade;
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "list", subcommands = {ListAgents.class, ListTools.class, ListPipelines.class, ListSkills.class})
    static class ListCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { CommandLine.usage(this, System.out); return 0; }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "agents")
    static class ListAgents implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listAgents()); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "tools")
    static class ListTools implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listTools()); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "pipelines")
    static class ListPipelines implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listPipelines()); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "skills")
    static class ListSkills implements Callable<Integer> {
        @CommandLine.ParentCommand ListCommand parent;
        @Override public Integer call() { return printList(parent.root.runtimeFacade.listSkills()); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "agent", subcommands = AgentRun.class)
    static class AgentCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "run", description = "Execute an agent")
    static class AgentRun implements Callable<Integer> {
        @CommandLine.ParentCommand AgentCommand parent;
        @CommandLine.Parameters(index = "0", description = "Agent name") String name;
        
        @CommandLine.Option(names = {"-i", "--input"}, 
                description = "Input JSON string or file path (for direct agent execution)")
        String input;
        
        @CommandLine.Option(names = {"-r", "--request"}, 
                description = "Natural language task request (for task-style execution)")
        String request;
        
        @CommandLine.Option(names = {"-c", "--context"}, 
                description = "Context data as JSON string (used with --request)")
        String context;
        
        @CommandLine.Option(names = {"--conversation"}, 
                description = "Continue existing conversation ID (for multi-turn dialogue)")
        String conversationId;
        
        @CommandLine.Option(names = {"-v", "--verbose"}, 
                description = "Show detailed agent reasoning and tool calls")
        boolean verbose;
        
        @Override 
        public Integer call() {
            // Validate: either --input or --request must be provided
            if (input == null && request == null) {
                System.err.println("❌ Error: Either --input or --request must be provided");
                System.err.println("   Use --input for direct JSON execution");
                System.err.println("   Use --request for natural language tasks");
                return 1;
            }
            
            if (input != null && request != null) {
                System.err.println("❌ Error: Cannot use both --input and --request together");
                return 1;
            }
            
            try {
                if (request != null) {
                    // Natural language task mode
                    return executeNaturalLanguageTask();
                } else {
                    // Direct agent execution mode
                    return run(parent.root.runtimeFacade, name, input, Mode.AGENT);
                }
            } catch (Exception e) {
                System.err.println("❌ Execution failed: " + e.getMessage());
                if (verbose) {
                    log.error("Failed to execute agent", e);
                }
                return 1;
            }
        }
        
        private Integer executeNaturalLanguageTask() throws Exception {
            ObjectMapper mapper = JacksonFactory.jsonMapper();
            RuntimeFacade facade = parent.root.runtimeFacade;
            
            System.out.println("🤖 Executing agent '" + name + "' with natural language request");
            System.out.println("📝 Request: " + request);
            System.out.println();
            
            // Build input JSON with request field
            var inputJson = mapper.createObjectNode();
            inputJson.put("request", request);
            
            // Add context if provided
            if (context != null) {
                try {
                    JsonNode contextNode = mapper.readTree(context);
                    inputJson.set("context", contextNode);
                } catch (Exception e) {
                    System.err.println("❌ Invalid context JSON: " + e.getMessage());
                    return 1;
                }
            }
            
            System.out.println("⏳ Agent is thinking and calling tools...");
            System.out.println();
            
            // Execute agent with conversation support
            RunResult result = facade.runAgent(name, inputJson, conversationId);
            
            if (result.getStatus() != ExecutionStatus.SUCCESS) {
                System.err.println("❌ Task failed: " + result.getError());
                return 1;
            }
            
            System.out.println("✅ Task completed successfully!");
            System.out.println();
            
            // Extract enhanced metadata from output if available
            JsonNode output = result.getOutput();
            
            // Show tool usage if available
            if (output.has("tools_used") && output.get("tools_used").isArray()) {
                var toolsUsed = new java.util.ArrayList<String>();
                output.get("tools_used").forEach(t -> toolsUsed.add(t.asText()));
                if (!toolsUsed.isEmpty()) {
                    System.out.println("🔧 Tools used: " + String.join(", ", toolsUsed));
                }
            }
            
            // Show iterations if available
            if (output.has("iterations")) {
                System.out.println("📊 Iterations: " + output.get("iterations").asInt());
            }
            
            if (output.has("tools_used") || output.has("iterations")) {
                System.out.println();
            }
            
            // Show reasoning in verbose mode
            if (verbose && output.has("reasoning") && !output.get("reasoning").asText().isEmpty()) {
                System.out.println("💭 Agent reasoning:");
                System.out.println(output.get("reasoning").asText());
                System.out.println();
            }
            
            // Show result
            System.out.println("📋 Result:");
            JsonNode resultNode = output.has("result") ? output.get("result") : output;
            String resultJson = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(resultNode);
            System.out.println(resultJson);
            System.out.println();
            
            // Show conversation ID for follow-ups
            if (conversationId != null) {
                System.out.println("💬 Continuing conversation: " + conversationId);
            }
            
            // Show execution ID
            if (result.getExecutionId() != null) {
                System.out.println("📂 Execution ID: " + result.getExecutionId());
                System.out.println("   Details: ./repo/_executions/" + result.getExecutionId());
            }
            
            return 0;
        }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "tool", subcommands = ToolRun.class)
    static class ToolCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "run")
    static class ToolRun implements Callable<Integer> {
        @CommandLine.ParentCommand ToolCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        @CommandLine.Option(names = "--input", required = true) String input;
        @Override public Integer call() { return run(parent.root.runtimeFacade, name, input, Mode.TOOL); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "pipeline", subcommands = PipelineRun.class)
    static class PipelineCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "skill", subcommands = {SkillRun.class, SkillValidate.class})
    static class SkillCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @Override public Integer call() { return 0; }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "run")
    static class SkillRun implements Callable<Integer> {
        @CommandLine.ParentCommand SkillCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        @CommandLine.Option(names = "--input", required = true) String input;
        @Override public Integer call() { return run(parent.root.runtimeFacade, name, input, Mode.SKILL); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "validate", description = "Validate a skill against agentskills.io spec")
    static class SkillValidate implements Callable<Integer> {
        @CommandLine.ParentCommand SkillCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        
        @Override
        public Integer call() {
            try {
                var repository = parent.root.runtimeFacade.getArtifactRepository();
                var manifest = repository.loadSkill(name);
                var validator = new org.hubbers.skill.SkillValidator();
                var result = validator.validate(manifest);
                
                if (result.isValid()) {
                    System.out.println("✓ Skill '" + name + "' is valid");
                    return 0;
                } else {
                    System.err.println("✗ Skill '" + name + "' validation failed:");
                    result.getErrors().forEach(err -> System.err.println("  - " + err));
                    return 1;
                }
            } catch (Exception e) {
                System.err.println("Validation error: " + e.getMessage());
                return 1;
            }
        }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "run")
    static class PipelineRun implements Callable<Integer> {
        @CommandLine.ParentCommand PipelineCommand parent;
        @CommandLine.Parameters(index = "0") String name;
        @CommandLine.Option(names = "--input", required = true) String input;
        @Override public Integer call() { return run(parent.root.runtimeFacade, name, input, Mode.PIPELINE); }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true, name = "web", description = "Start web UI and API")
    static class WebCommand implements Callable<Integer> {
        @CommandLine.ParentCommand HubbersCommand root;
        @CommandLine.Option(names = "--port", defaultValue = "7070") int port;

        @Override
        public Integer call() {
            var appConfig = new ConfigLoader(root.repoPath).load();
            var manifestFileService = new ManifestFileService(Path.of(appConfig.getRepoRoot()));

            WebServer webServer = new WebServer(root.runtimeFacade, manifestFileService, new ManifestValidator(), root.repoPath);

            // Wire MCP server
            var objectMapper = JacksonFactory.jsonMapper();
            var mcpToolProvider = new McpToolProvider(root.runtimeFacade.getArtifactRepository(), objectMapper);
            var mcpPromptProvider = new McpPromptProvider(root.runtimeFacade.getArtifactRepository());
            var mcpResourceProvider = new org.hubbers.mcp.McpResourceProvider(
                    root.runtimeFacade.getArtifactRepository(), Path.of(appConfig.getRepoRoot()));
            var mcpHandler = new McpRequestHandler(mcpToolProvider, mcpPromptProvider, mcpResourceProvider, root.runtimeFacade, objectMapper);
            webServer.setMcpRequestHandler(mcpHandler);

            // Wire OpenAI-compatible proxy (/v1/chat/completions, /v1/models)
            var openAiProxy = new org.hubbers.web.OpenAiCompatibleProxy(root.runtimeFacade, mcpToolProvider, objectMapper);
            webServer.setOpenAiProxy(openAiProxy);

            webServer.start(port);


            System.out.println("Hubbers Web UI available at http://localhost:" + port);
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 1;
            }
            return 0;
        }
    }

    private enum Mode { AGENT, TOOL, PIPELINE, SKILL }

    private static Integer run(RuntimeFacade facade, String name, String inputSource, Mode mode) {
        ObjectMapper mapper = JacksonFactory.jsonMapper();
        try {
            // Check if input is a file or direct JSON
            JsonNode input;
            File inputFile = new File(inputSource);
            if (inputFile.exists() && inputFile.isFile()) {
                // Input is a file path
                input = mapper.readTree(Files.readString(inputFile.toPath()));
            } else {
                // Input is direct JSON string
                input = mapper.readTree(inputSource);
            }
            
            // Special handling for demo tools/pipelines - add default required fields if missing
            if (mode == Mode.TOOL && "demo.calculator".equals(name)) {
                if (!input.has("command")) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("command", "echo");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) input).set("args", 
                        mapper.createArrayNode().add("Calculator form test"));
                }
            }
            if (mode == Mode.PIPELINE && "demo.forms.test".equals(name)) {
                if (!input.has("feeds")) {
                    var feedsArray = mapper.createArrayNode();
                    feedsArray.add("https://news.ycombinator.com/rss");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) input).set("feeds", feedsArray);
                }
            }
            
            // Check for forms.before in manifest
            FormTrigger forms = getFormTrigger(facade, name, mode);
            if (forms != null && forms.getBefore() != null) {
                try {
                    CliFormService formService = new CliFormService();
                    Map<String, Object> formData = formService.collectFormData(forms.getBefore());
                    
                    // Merge form data with input
                    input = mergeFormDataWithInput(mapper, input, formData);
                    
                } catch (Exception e) {
                    System.err.println("Form collection failed: " + e.getMessage());
                    return 1;
                }
            }
            
            RunResult result = switch (mode) {
                case AGENT -> facade.runAgent(name, input);
                case TOOL -> facade.runTool(name, input);
                case PIPELINE -> facade.runPipeline(name, input);
                case SKILL -> facade.runSkill(name, input);
            };
            if (result.getStatus() == ExecutionStatus.SUCCESS) {
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.getOutput()));
                return 0;
            }
            System.err.println(result.getError());
            return 1;
        } catch (IOException e) {
            System.err.println("Input read/parse failed: " + e.getMessage());
            return 1;
        }
    }
    
    private static FormTrigger getFormTrigger(RuntimeFacade facade, String name, Mode mode) {
        try {
            return switch (mode) {
                case AGENT -> {
                    var manifest = facade.getArtifactRepository().loadAgent(name);
                    yield manifest.getForms();
                }
                case TOOL -> {
                    var manifest = facade.getArtifactRepository().loadTool(name);
                    yield manifest.getForms();
                }
                case PIPELINE -> {
                    var manifest = facade.getArtifactRepository().loadPipeline(name);
                    yield manifest.getForms();
                }
                case SKILL -> null; // Skills don't support forms (yet)
            };
        } catch (Exception e) {
            return null;
        }
    }
    
    private static JsonNode mergeFormDataWithInput(ObjectMapper mapper, JsonNode originalInput, Map<String, Object> formData) {
        ObjectNode merged = originalInput != null && originalInput.isObject() 
            ? (ObjectNode) originalInput.deepCopy()
            : mapper.createObjectNode();
        
        // Merge form data into input
        formData.forEach((key, value) -> {
            merged.set(key, mapper.valueToTree(value));
        });
        
        return merged;
    }

    private static Integer printList(List<String> values) {
        values.forEach(System.out::println);
        return 0;
    }
}
