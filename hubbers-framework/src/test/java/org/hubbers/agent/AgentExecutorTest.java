package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.agent.InputDefinition;
import org.hubbers.manifest.agent.Instructions;
import org.hubbers.manifest.agent.ModelConfig;
import org.hubbers.manifest.agent.OutputDefinition;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.common.SchemaDefinition;
import org.hubbers.model.ModelProvider;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.model.ModelRequest;
import org.hubbers.model.ModelResponse;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.SchemaValidator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorTest {

    private final ObjectMapper mapper = JacksonFactory.jsonMapper();

    @Test
    void returnsSuccessWhenModelReturnsValidJson() throws Exception {
        AgentManifest manifest = buildManifest();
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(
                providerReturning("{\"result\":\"ok\"}")
        ));

        AgentExecutor executor = new AgentExecutor(registry, new AgentPromptBuilder(), new SchemaValidator(), mapper);
        JsonNode input = mapper.readTree("{\"text\":\"hello\"}");
        RunResult result = executor.execute(manifest, input);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("ok", result.getOutput().path("result").asText());
    }

    @Test
    void returnsFailureWhenModelReturnsInvalidJson() throws Exception {
        AgentManifest manifest = buildManifest();
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(
                providerReturning("not-json")
        ));

        AgentExecutor executor = new AgentExecutor(registry, new AgentPromptBuilder(), new SchemaValidator(), mapper);
        JsonNode input = mapper.readTree("{\"text\":\"hello\"}");
        RunResult result = executor.execute(manifest, input);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getError().contains("Model output is not valid JSON"));
    }

    @Test
    void returnsFailureWhenJsonDoesNotMatchOutputSchema() throws Exception {
        AgentManifest manifest = buildManifest();
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(
                providerReturning("{\"result\":123}")
        ));

        AgentExecutor executor = new AgentExecutor(registry, new AgentPromptBuilder(), new SchemaValidator(), mapper);
        JsonNode input = mapper.readTree("{\"text\":\"hello\"}");
        RunResult result = executor.execute(manifest, input);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getError().contains("Invalid type for field result"));
    }

    private ModelProvider providerReturning(String content) {
        return new ModelProvider() {
            @Override
            public String providerName() {
                return "stub";
            }

            @Override
            public ModelResponse generate(ModelRequest request) {
                ModelResponse response = new ModelResponse();
                response.setContent(content);
                response.setModel("stub-model");
                response.setLatencyMs(1L);
                return response;
            }
        };
    }

    private AgentManifest buildManifest() {
        AgentManifest manifest = new AgentManifest();

        Metadata metadata = new Metadata();
        metadata.setName("test.agent");
        metadata.setVersion("1.0.0");
        manifest.setAgent(metadata);

        ModelConfig model = new ModelConfig();
        model.setProvider("stub");
        model.setName("stub-model");
        manifest.setModel(model);

        Instructions instructions = new Instructions("Return JSON");
        manifest.setInstructions(instructions);

        InputDefinition inputDefinition = new InputDefinition();
        SchemaDefinition inputSchema = new SchemaDefinition();
        inputSchema.setType("object");
        inputSchema.setProperties(new LinkedHashMap<>());
        PropertyDefinition text = new PropertyDefinition();
        text.setType("string");
        text.setRequired(true);
        inputSchema.getProperties().put("text", text);
        inputDefinition.setSchema(inputSchema);
        manifest.setInput(inputDefinition);

        OutputDefinition outputDefinition = new OutputDefinition();
        SchemaDefinition outputSchema = new SchemaDefinition();
        outputSchema.setType("object");
        outputSchema.setProperties(new LinkedHashMap<>());
        PropertyDefinition result = new PropertyDefinition();
        result.setType("string");
        result.setRequired(true);
        outputSchema.getProperties().put("result", result);
        outputDefinition.setSchema(outputSchema);
        manifest.setOutput(outputDefinition);

        return manifest;
    }
}
