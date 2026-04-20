package org.hubbers.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.agent.Instructions;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.manifest.tool.ToolManifest;
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
    @Disabled("This test demonstrates how to define and execute a pipeline entirely from code, without relying on manifest files. It's useful for testing or dynamic pipeline creation scenarios.")
    void executePipelineFromCode() throws Exception {
        
        ToolManifest serperSearch = new ToolManifest();
        serperSearch.getTool().setName("serper.search"); 

        ToolManifest fileRead = new ToolManifest();
        fileRead.getTool().setName("file.read"); 


        // Define the Researcher Agent
        AgentManifest researcher = new AgentManifest();
        //researcher.setId("researcher");
        researcher.getAgent().setName("Senior Research Analyst");
        researcher.setInstructions(new Instructions(
            "Uncover developments in AI frameworks. Find 3 key innovations."
        ));
        researcher.setTools(List.of("serper.search", "file.read")); // IDs of existing tools

        // Define the Writer Agent
        AgentManifest writer = new AgentManifest();
        //writer.setId("writer");
        writer.getAgent().setName("Technical Content Strategist");
        writer.setInstructions(new Instructions(
            "Translate technical concepts into executive summaries in Markdown."
        ));

        PipelineManifest pipeline = new PipelineManifest();
        pipeline.getPipeline().setName("research-pipeline");

        // Step 1: Research
        PipelineStep step1 = new PipelineStep();
        step1.setId("research_task");
        step1.setAgent("Senior Research Analyst");
        step1.setInputMapping(Map.of("query", "Analyze the repository for innovations."));

        // Step 2: Report (using output from step 1)
        PipelineStep step2 = new PipelineStep();
        step2.setId("report_task");
        step2.setAgent("Technical Content Strategist");
        step2.setInputMapping(Map.of("content", "${steps.research_task.output}"));

        pipeline.setSteps(List.of(step1, step2));

        // Initialize pointing to a workspace directory for logs/executions
        RuntimeFacade hubbers = new RuntimeFacade(Path.of("./workspace"));
        
        // Access the repository to register your code-defined agents/pipelines
        hubbers.getArtifactRepository().addAgent(researcher);
        hubbers.getArtifactRepository().addAgent(writer);
        hubbers.getArtifactRepository().addTool(serperSearch);
        hubbers.getArtifactRepository().addTool(fileRead);
        hubbers.getArtifactRepository().addPipeline(pipeline);

        // Run the pipeline
        JsonNode inputsNode = mapper.convertValue(Map.of(), JsonNode.class);
        RunResult result = hubbers.runPipeline("research-pipeline", inputsNode);
        System.out.println("Result: " + result.getOutput());
    }

}
