package org.hubbers.react;


import lombok.extern.slf4j.Slf4j;
import org.hubbers.react.model.ModelResponse;
import org.hubbers.react.model.ModelProvider;
import org.hubbers.react.tool.ToolCallHandler;
import org.hubbers.react.tool.ToolIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class HubberAgentLoopTest {

    private static final String AGENT_RESOURCE = "/org/hubbers/react/qwen2.5-7b-instruct-q4_k_m/agent.yaml";

    // =========================================================================
    // Interactive main
    // =========================================================================

    public static void main(String[] args) throws IOException {

        Scanner scanner     = new Scanner(System.in);

        HubberAgentLoop agent = fromConfig(AGENT_RESOURCE, scanner);
        System.out.println("🤖 Hubber Agent Loop Pronto (Modello: " + agent.getModelName() + ").");

        while (true) {
            System.out.print("\n👤 Utente: ");
            String userInput = scanner.nextLine();
            if (userInput.equalsIgnoreCase("exit")) break;
            agent.runLoop(userInput);
        }
        scanner.close();
    }

    public static HubberAgentLoop fromConfig(String agentResource, Scanner scanner) throws IOException {
        ReactAgentConfig cfg = ReactAgentConfig.fromResource(agentResource);
        Path memoryPath = cfg.resolveWritable(cfg.prompts().memory());
        ReactAgentConfig.ToolRagConfig ragCfg = cfg.toolRagOrDefault();

        ToolIndex toolIndex = null;
        Path discoveryPromptPath = null;
        if (ragCfg.enabled() && ragCfg.discoveryPrompt() != null) {
            toolIndex = ToolIndex.from(cfg.resolvePrompt(cfg.prompts().tools()));
            discoveryPromptPath = cfg.resolvePrompt(ragCfg.discoveryPrompt());
        }

        return new HubberAgentLoop(
            new MinimalLlamaCppProvider(),
            new MinimalFileToolCallHandler(memoryPath),
            new ArtifactCatalogBuilder(cfg.resolvePrompt(cfg.prompts().tools())),
            new InMemoryMemory(),
            cfg.resolvePrompt(cfg.prompts().soul()),
            memoryPath,
            cfg.model().name(),
            cfg.config().maxIterations(),
            new MinimalApprovalHandler(scanner),
            cfg.resolvePrompt(cfg.prompts().observation()),
            cfg.resolvePrompt(cfg.prompts().reflection()),
            cfg.resolvePrompt(cfg.prompts().rejection()),
            toolIndex,
            ragCfg.topK(),
            discoveryPromptPath
        );
    }

    // =========================================================================
    // JUnit tests — two model stubs
    // =========================================================================

    @TempDir
    Path tempDir;

    private ReactAgentConfig cfg;
    private Path memoryFile;

    @BeforeEach
    void setUpConfig() throws IOException {
        cfg = ReactAgentConfig.fromResource(AGENT_RESOURCE);
        memoryFile = tempDir.resolve("memory.md");
        Files.writeString(memoryFile, "");
    }

    /**
     * Builds a fully-wired {@link HubberAgentLoop} with a custom provider and handler,
     * using the prompt paths from {@code agent.yaml} but a temp memory file.
     */
    private HubberAgentLoop buildAgent(ModelProvider provider, ToolCallHandler toolHandler) throws IOException {
        return new HubberAgentLoop(
                provider,
                toolHandler,
                new ArtifactCatalogBuilder(cfg.resolvePrompt(cfg.prompts().tools())),
                new InMemoryMemory(),
                cfg.resolvePrompt(cfg.prompts().soul()),
                memoryFile,
                "stub-model",
                cfg.config().maxIterations(),
                (tool, call) -> true,   // auto-approve all tools
                cfg.resolvePrompt(cfg.prompts().observation()),
                cfg.resolvePrompt(cfg.prompts().reflection()),
                cfg.resolvePrompt(cfg.prompts().rejection()),
                null,  // toolIndex: RAG disabled in unit tests (stub model)
                5,
                null   // discoveryPromptPath
        );
    }

    /**
     * Simulates qwen2.5-3b behaviour: the model produces a final answer immediately
     * on the first turn — no tool call emitted.
     * Verifies the loop completes cleanly without any tool invocation.
     */
    @Test
    @DisplayName("qwen2.5-3b stub: direct answer, no tool call")
    void testModel_qwen2_5_3b_directAnswer() throws IOException {
        AtomicBoolean toolCalled = new AtomicBoolean(false);

        ModelProvider stub = req -> {
            ModelResponse r = new ModelResponse();
            r.setContent("Ciao! Sono pronto ad aiutarti.");
            return r;
        };
        ToolCallHandler recorder = (name, args) -> {
            toolCalled.set(true);
            return new MinimalFileToolCallHandler(memoryFile).handle(name, args);
        };

        assertDoesNotThrow(() -> buildAgent(stub, recorder).runLoop("Ciao"));
        assertFalse(toolCalled.get(), "qwen2.5-3b stub should not invoke any tool");
    }

    /**
     * Simulates qwen3:4b behaviour: the model first emits a {@code dir_exists} tool
     * call to inspect {@code tempDir}, receives the observation, then produces a final
     * answer on the second turn.
     * Verifies the loop calls the tool handler exactly once.
     */
    @Test
    @DisplayName("qwen3:4b stub: tool call on first turn, final answer on second")
    void testModel_qwen3_4b_toolCallThenAnswer() throws IOException {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicInteger toolInvocations = new AtomicInteger(0);

        String escapedPath = tempDir.toString().replace("\\", "\\\\");

        ModelProvider stub = req -> {
            ModelResponse r = new ModelResponse();
            r.setContent(callCount.getAndIncrement() == 0
                    ? "<thought>Verifico se la cartella esiste.</thought>"
                      + "<call:dir_exists foldername=\"" + escapedPath + "\"/>"
                    : "Sì, la cartella esiste.");
            return r;
        };
        ToolCallHandler recorder = (name, args) -> {
            toolInvocations.incrementAndGet();
            return new MinimalFileToolCallHandler(memoryFile).handle(name, args);
        };

        assertDoesNotThrow(() -> buildAgent(stub, recorder).runLoop(
                "La cartella " + tempDir + " esiste?"));
        assertEquals(1, toolInvocations.get(), "qwen3:4b stub should invoke exactly one tool");
    }
}