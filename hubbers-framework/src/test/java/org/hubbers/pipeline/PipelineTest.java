package org.hubbers.pipeline;

import java.nio.file.Path;
import java.util.Map;

import org.hubbers.annotation.Agent;
import org.hubbers.annotation.Pipeline;
import org.hubbers.annotation.Task;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class PipelineTest {

    private final ObjectMapper mapper = JacksonFactory.jsonMapper();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Setup code before each test
    }

    @AfterEach
    void tearDown() {
    
    }

    @Test
    void executePipeline() throws Exception {
        RuntimeFacade hubbers = new RuntimeFacade(Path.of("hubbers-repo/src/main/resources/repo"));

        Path sourceDir = tempDir.resolve("source");
        Path backupDir = tempDir.resolve("backup");
        java.nio.file.Files.createDirectories(sourceDir);
        java.nio.file.Files.writeString(sourceDir.resolve("sample.txt"), "backup me");

        Map<String, Object> inputs = Map.of(
            "source_dir", sourceDir.toString(),
            "backup_dir", backupDir.toString(),
            "file_pattern", ".*\\.txt",
            "file_name", "sample.txt"
        );

        JsonNode inputsNode = mapper.convertValue(inputs, JsonNode.class);
        RunResult result = hubbers.runPipeline("file.backup", inputsNode);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(org.hubbers.execution.ExecutionStatus.SUCCESS, result.getStatus());
        Assertions.assertTrue(java.nio.file.Files.exists(backupDir.resolve("sample.txt")));
    }

    @Test
    @Disabled("Demonstrates @Pipeline DSL for code-defined pipelines. Requires live LLM and tool access.")
    void executePipelineFromCode() throws Exception {

        // ------------------------------------------------------------------ //
        // Define the pipeline using the @Pipeline / @Agent / @Task DSL.       //
        // All wiring is handled by FlowRunner — no manual manifest assembly.  //
        // ------------------------------------------------------------------ //
        @Pipeline("research-pipeline")
        class ResearchFlow {

            @Agent
            AgentManifest researcher() {
                return AgentManifest.builder()
                    .name("Senior Research Analyst")
                    .instructions("you are a researcher analyst", "Uncover developments in AI frameworks. Find 3 key innovations.")
                    .tools("serper.search", "file.read")
                    .build();
            }

            @Agent
            AgentManifest writer() {
                return AgentManifest.builder()
                    .name("Technical Content Strategist")
                    .instructions("You are Technical Content Strategist", "Translate technical concepts into executive summaries in Markdown.")
                    .build();
            }

            @Task(order = 1)
            PipelineStep researchTask() {
                return PipelineStep.builder()
                    .id("research_task")
                    .agent("Senior Research Analyst")
                    .input("query", "Analyze the repository for innovations.")
                    .build();
            }

            @Task(order = 2)
            PipelineStep reportTask() {
                return PipelineStep.builder()
                    .id("report_task")
                    .agent("Technical Content Strategist")
                    .input("content", "${steps.research_task.output}")
                    .build();
            }
        }

        RuntimeFacade hubbers = new RuntimeFacade(Path.of("./workspace"));
        JsonNode input = mapper.convertValue(Map.of(), JsonNode.class);

        RunResult result = hubbers.runFlow(new ResearchFlow(), input);

        Assertions.assertNotNull(result);
        System.out.println("Result: " + result.getOutput());
    }
}
