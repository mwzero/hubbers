package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.validation.SchemaValidator;
import org.hubbers.validation.ValidationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolExecutor {
    private final Map<String, ToolDriver> drivers = new HashMap<>();
    private final SchemaValidator schemaValidator;

    public ToolExecutor(List<ToolDriver> drivers, SchemaValidator schemaValidator) {
        for (ToolDriver driver : drivers) {
            this.drivers.put(driver.type(), driver);
        }
        this.schemaValidator = schemaValidator;
    }

    public RunResult execute(ToolManifest manifest, JsonNode input) {
        ValidationResult inputValidation = schemaValidator.validate(input, manifest.getInput().getSchema());
        if (!inputValidation.isValid()) {
            return RunResult.failed(String.join(", ", inputValidation.getErrors()));
        }
        ToolDriver driver = drivers.get(manifest.getType());
        if (driver == null) {
            return RunResult.failed("Unsupported tool type: " + manifest.getType());
        }
        JsonNode output = driver.execute(manifest, input);
        ValidationResult outputValidation = schemaValidator.validate(output, manifest.getOutput().getSchema());
        if (!outputValidation.isValid()) {
            return RunResult.failed(String.join(", ", outputValidation.getErrors()));
        }
        return RunResult.success(output);
    }
}
