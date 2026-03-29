package org.hubbers.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.execution.ExecutionMetadata;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.model.ModelProvider;
import org.hubbers.model.ModelProviderRegistry;
import org.hubbers.model.ModelRequest;
import org.hubbers.model.ModelResponse;
import org.hubbers.validation.SchemaValidator;
import org.hubbers.validation.ValidationResult;

public class AgentExecutor {
    private final ModelProviderRegistry modelProviderRegistry;
    private final AgentPromptBuilder promptBuilder;
    private final SchemaValidator schemaValidator;
    private final ObjectMapper mapper;

    public AgentExecutor(ModelProviderRegistry modelProviderRegistry,
                         AgentPromptBuilder promptBuilder,
                         SchemaValidator schemaValidator,
                         ObjectMapper mapper) {
        this.modelProviderRegistry = modelProviderRegistry;
        this.promptBuilder = promptBuilder;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
    }

    public RunResult execute(AgentManifest manifest, JsonNode input) {
        ValidationResult inputValidation = schemaValidator.validate(input, manifest.getInput().getSchema());
        if (!inputValidation.isValid()) {
            return RunResult.failed(String.join(", ", inputValidation.getErrors()));
        }

        AgentRunContext context = new AgentRunContext();
        context.setManifest(manifest);
        context.setInput(input);

        ModelRequest request = promptBuilder.build(context);
        ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
        ModelResponse modelResponse = provider.generate(request);

        JsonNode output;
        try {
            output = mapper.readTree(modelResponse.getContent());
        } catch (Exception e) {
            return RunResult.failed("Model output is not valid JSON: " + e.getMessage());
        }
        ValidationResult outputValidation = schemaValidator.validate(output, manifest.getOutput().getSchema());
        if (!outputValidation.isValid()) {
            return RunResult.failed(String.join(", ", outputValidation.getErrors()));
        }

        RunResult result = RunResult.success(output);
        ExecutionMetadata metadata = new ExecutionMetadata();
        metadata.setStartedAt(System.currentTimeMillis() - modelResponse.getLatencyMs());
        metadata.setEndedAt(System.currentTimeMillis());
        metadata.setDetails("model=" + modelResponse.getModel() + ", latencyMs=" + modelResponse.getLatencyMs());
        result.setMetadata(metadata);
        return result;
    }
}
