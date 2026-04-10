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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);
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
        log.debug("Executing agent: {}", manifest.getAgent().getName());
        
        ValidationResult inputValidation = schemaValidator.validate(input, manifest.getInput().getSchema());
        if (!inputValidation.isValid()) {
            return RunResult.failed(String.join(", ", inputValidation.getErrors()));
        }

        AgentRunContext context = new AgentRunContext();
        context.setManifest(manifest);
        context.setInput(input);

        ModelRequest request = promptBuilder.build(context);
        ModelProvider provider = modelProviderRegistry.get(manifest.getModel().getProvider());
        
        log.debug("Calling LLM: provider={}, model={}", 
            manifest.getModel().getProvider(), manifest.getModel().getName());
        ModelResponse modelResponse = provider.generate(request);
        log.debug("LLM response received: latencyMs={}", modelResponse.getLatencyMs());

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
