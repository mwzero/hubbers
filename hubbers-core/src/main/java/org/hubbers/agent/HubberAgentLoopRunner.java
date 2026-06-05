package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.config.OllamaConfig;
import org.hubbers.manifest.agent.InputDefinition;
import org.hubbers.manifest.agent.OutputDefinition;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.model.OllamaModelProvider;
import org.hubbers.react.AgentConfig;
import org.hubbers.react.HubberAgentLoop;
import org.hubbers.react.execution.ExecutionStatus;
import org.hubbers.react.execution.RunResult;
import org.hubbers.react.model.FunctionCall;
import org.hubbers.react.model.FunctionDefinition;
import org.hubbers.react.model.Message;
import org.hubbers.react.model.ModelRequest;
import org.hubbers.react.model.ModelResponse;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolExecutor;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive standalone mode for the {@link HubberAgentLoop}.
 *
 * <p>This class holds the no-arg constructor and {@link #main(String[])} entry-point
 * that were previously part of {@code HubberAgentLoop}.  They live in {@code hubbers-core}
 * because they require concrete platform types ({@code OllamaModelProvider},
 * {@code ToolExecutor}, etc.) that {@code hubbers-react} does not depend on.</p>
 *
 * <p>For production wiring, use {@link HubberAgentLoop} directly via {@code Bootstrap}.</p>
 */
@Slf4j
public class HubberAgentLoopRunner {

    private static final String INTERACTIVE_MODEL = "qwen3:4b";
    private static final int INTERACTIVE_MAX_ITERATIONS = 5;

    private static final String MEMORY_RESOURCE        = "/org/hubbers/agent/memory.md";
    private static final String TOOLS_RESOURCE         = "/org/hubbers/agent/tools.md";
    private static final String SYSTEM_PROMPT_RESOURCE = "/org/hubbers/agent/system-prompt.md";

    private final OllamaModelProvider interactiveModelProvider;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper mapper;
    private final Map<String, ToolManifest> toolManifests;
    private final List<FunctionDefinition> functions;
    private final List<Message> conversationHistory = new ArrayList<>();
    private String systemPromptTemplate = "";

    /**
     * Creates an interactive runner connected to a local Ollama instance.
     */
    public HubberAgentLoopRunner() {
        OllamaConfig config = new OllamaConfig();
        config.setDefaultModel(INTERACTIVE_MODEL);
        this.mapper = JacksonFactory.jsonMapper();
        HttpClient httpClient = HttpClient.newHttpClient();
        this.interactiveModelProvider = new OllamaModelProvider(httpClient, config, mapper);

        SchemaValidator validator = new SchemaValidator();
        this.toolExecutor = new ToolExecutor(
                List.of(new FileWriteDriver(mapper),
                        new FileReadDriver(mapper),
                        new MemoryAppendDriver(mapper)),
                validator);

        this.toolManifests = buildToolManifests();
        this.functions = buildFunctionDefinitions(mapper);
    }

    /**
     * Starts the interactive ReAct loop. Type {@code exit} to quit.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        HubberAgentLoopRunner runner = new HubberAgentLoopRunner();
        System.out.println("🤖 Hubber Agent Loop Pronto (Modello: " + INTERACTIVE_MODEL + ").");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n👤 Utente: ");
            String userInput = scanner.nextLine();
            if (userInput.equalsIgnoreCase("exit")) break;
            runner.runInteractiveLoop(userInput);
        }
        scanner.close();
    }

    /**
     * Runs one interactive ReAct turn for the given user request.
     *
     * @param userRequest the user's natural-language request
     */
    public void runInteractiveLoop(String userRequest) {
        initializeInteractive();
        conversationHistory.add(Message.user(userRequest));

        int iteration = 0;
        boolean taskComplete = false;

        while (!taskComplete && iteration < INTERACTIVE_MAX_ITERATIONS) {
            iteration++;
            System.out.println("\n🔄 [Loop Iterazione " + iteration + "] L'agente sta pensando...");

            // THINK
            ModelRequest request = new ModelRequest();
            request.setModel(INTERACTIVE_MODEL);
            request.setThink(false);
            request.setMessages(conversationHistory);
            request.setFunctions(functions);

            // ACT
            ModelResponse response = interactiveModelProvider.generate(request);

            // OBSERVE
            if (response.getFunctionCalls() != null && !response.getFunctionCalls().isEmpty()) {
                conversationHistory.add(
                        Message.assistantWithToolCalls(response.getContent(), response.getFunctionCalls()));

                for (FunctionCall call : response.getFunctionCalls()) {
                    System.out.println("🛠️ Tool: " + call.getName() + " " + call.getArguments());

                    ToolManifest manifest = toolManifests.get(call.getName());
                    String resultContent;
                    if (manifest == null) {
                        resultContent = "{\"status\":\"error\",\"message\":\"Tool non trovato: "
                                + call.getName() + "\"}";
                    } else {
                        RunResult result = toolExecutor.execute(manifest, call.getArguments());
                        resultContent = result.getStatus() == ExecutionStatus.SUCCESS
                                ? result.getOutput().toString()
                                : "{\"status\":\"error\",\"message\":\"" + result.getError() + "\"}";
                    }

                    System.out.println("👁️ Osservazione: " + resultContent);
                    conversationHistory.add(
                            Message.tool(call.getId(), call.getName(), resultContent));
                }
                // REFLECT — continue
            } else {
                // REFLECT — done
                System.out.println("\n🤖 Risposta Finale: " + response.getContent());
                conversationHistory.add(Message.assistant(response.getContent()));
                taskComplete = true;
            }
        }

        if (iteration >= INTERACTIVE_MAX_ITERATIONS) {
            System.out.println("⚠️ Loop interrotto: raggiunto il limite massimo di iterazioni.");
        }
    }

    private void initializeInteractive() {
        String memoryContent = loadResource(MEMORY_RESOURCE, "Nessun dato in memoria.");
        String toolsContent  = loadResource(TOOLS_RESOURCE, "");
        String template      = loadResource(SYSTEM_PROMPT_RESOURCE, "{{MEMORY}}\n\n{{CONVERSATION}}");

        systemPromptTemplate = template
                .replace("{{MEMORY}}", memoryContent)
                .replace("{{TOOLS}}", toolsContent);

        conversationHistory.clear();
        conversationHistory.add(Message.system(systemPromptTemplate));
    }

    private String loadResource(String resourcePath, String fallback) {
        try (InputStream is = HubberAgentLoopRunner.class.getResourceAsStream(resourcePath)) {
            if (is != null) return new String(is.readAllBytes());
        } catch (IOException e) {
            log.warn("Cannot read resource {}: {}", resourcePath, e.getMessage());
        }
        return fallback;
    }

    private Map<String, ToolManifest> buildToolManifests() {
        return Map.of(
                "file_write",    createManifest("file_write"),
                "file_read",     createManifest("file_read"),
                "memory_append", createManifest("memory_append"));
    }

    private ToolManifest createManifest(String type) {
        ToolManifest m = new ToolManifest();
        m.setType(type);
        m.setInput(new InputDefinition());
        m.setOutput(new OutputDefinition());
        return m;
    }

    private List<FunctionDefinition> buildFunctionDefinitions(ObjectMapper objectMapper) {
        return List.of(
                FunctionDefinition.builder()
                        .name("file_write")
                        .description("Scrive un file sul filesystem con il contenuto specificato.")
                        .parameters(buildSimpleSchema(objectMapper,
                                Map.of("filename", "string", "content", "string")))
                        .build(),
                FunctionDefinition.builder()
                        .name("file_read")
                        .description("Legge il contenuto di un file dal filesystem.")
                        .parameters(buildSimpleSchema(objectMapper, Map.of("filename", "string")))
                        .build(),
                FunctionDefinition.builder()
                        .name("memory_append")
                        .description("Aggiunge una nota alla memoria a lungo termine dell'agente.")
                        .parameters(buildSimpleSchema(objectMapper, Map.of("text", "string")))
                        .build());
    }

    private JsonNode buildSimpleSchema(ObjectMapper objectMapper, Map<String, String> properties) {
        com.fasterxml.jackson.databind.node.ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        com.fasterxml.jackson.databind.node.ObjectNode props = schema.putObject("properties");
        properties.forEach((key, type) -> props.putObject(key).put("type", type));
        com.fasterxml.jackson.databind.node.ArrayNode required = schema.putArray("required");
        properties.keySet().forEach(required::add);
        return schema;
    }

    // =========================================================================
    // Inner ToolDriver implementations (interactive mode only)
    // =========================================================================

    /**
     * Writes content to a file.
     *
     * <p><b>Security note</b>: The filename comes from model output and is not sanitised.
     * For development use only; do not expose in production without path canonicalization.</p>
     */
    private static class FileWriteDriver implements ToolDriver {
        private final ObjectMapper mapper;
        FileWriteDriver(ObjectMapper mapper) { this.mapper = mapper; }

        @Override public String type() { return "file_write"; }

        @Override
        public JsonNode execute(ToolManifest manifest, JsonNode input) {
            try {
                String filename = input.path("filename").asText();
                String content  = input.path("content").asText();
                Files.writeString(Paths.get(filename), content);
                return mapper.createObjectNode()
                        .put("status", "success")
                        .put("message", "File '" + filename + "' scritto correttamente.");
            } catch (IOException e) {
                return mapper.createObjectNode()
                        .put("status", "error").put("message", e.getMessage());
            }
        }
    }

    /**
     * Reads content from a file.
     *
     * <p><b>Security note</b>: The filename comes from model output and is not sanitised.
     * For development use only.</p>
     */
    private static class FileReadDriver implements ToolDriver {
        private final ObjectMapper mapper;
        FileReadDriver(ObjectMapper mapper) { this.mapper = mapper; }

        @Override public String type() { return "file_read"; }

        @Override
        public JsonNode execute(ToolManifest manifest, JsonNode input) {
            try {
                Path path = Paths.get(input.path("filename").asText());
                if (!Files.exists(path)) {
                    return mapper.createObjectNode()
                            .put("status", "error").put("message", "File non trovato.");
                }
                return mapper.createObjectNode()
                        .put("status", "success")
                        .put("content", Files.readString(path));
            } catch (IOException e) {
                return mapper.createObjectNode()
                        .put("status", "error").put("message", e.getMessage());
            }
        }
    }

    /**
     * Appends a note to the agent's long-term {@code memory.md} file.
     */
    private static class MemoryAppendDriver implements ToolDriver {
        private final ObjectMapper mapper;
        MemoryAppendDriver(ObjectMapper mapper) { this.mapper = mapper; }

        @Override public String type() { return "memory_append"; }

        @Override
        public JsonNode execute(ToolManifest manifest, JsonNode input) {
            try {
                String text = input.path("text").asText();
                URL classUrl = HubberAgentLoopRunner.class.getResource(MEMORY_RESOURCE);
                if (classUrl == null) {
                    return mapper.createObjectNode()
                            .put("status", "error")
                            .put("message", "File di memoria non trovato nel classpath.");
                }
                Path classPath = Paths.get(classUrl.toURI());
                Path targetDir = classPath;
                while (targetDir != null
                        && !"target".equals(targetDir.getFileName().toString())) {
                    targetDir = targetDir.getParent();
                }
                Path path;
                if (targetDir != null) {
                    String srcResources = Files.isDirectory(targetDir.resolve("test-classes"))
                            ? "test/resources" : "main/resources";
                    path = targetDir.getParent()
                            .resolve("src").resolve(srcResources)
                            .resolve("org").resolve("hubbers").resolve("agent")
                            .resolve("memory.md");
                } else {
                    path = classPath;
                }
                Files.writeString(path, "\n- " + text, StandardOpenOption.APPEND);
                return mapper.createObjectNode()
                        .put("status", "success")
                        .put("message", "Nota aggiunta con successo a memory.md.");
            } catch (IOException | URISyntaxException e) {
                return mapper.createObjectNode()
                        .put("status", "error").put("message", e.getMessage());
            }
        }
    }
}
