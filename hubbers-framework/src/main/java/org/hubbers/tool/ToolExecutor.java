package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.validation.SchemaValidator;
import org.hubbers.validation.ValidationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for tool artifacts with automatic driver routing and validation.
 * 
 * <p>ToolExecutor manages a registry of {@link ToolDriver} implementations and
 * routes tool execution to the appropriate driver based on the tool's type.
 * It handles input/output validation against JSON schemas defined in the manifest.</p>
 * 
 * <p>Execution flow:
 * <ol>
 *   <li>Validate input against tool's input schema</li>
 *   <li>Look up appropriate driver based on tool type</li>
 *   <li>Delegate execution to driver</li>
 *   <li>Validate output against tool's output schema</li>
 *   <li>Return RunResult with success/failure status</li>
 * </ol>
 * </p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Initialize with drivers
 * List<ToolDriver> drivers = List.of(
 *     new HttpToolDriver(httpClient, mapper),
 *     new RssToolDriver(httpClient, mapper),
 *     new FirecrawlToolDriver(mapper, config)
 * );
 * ToolExecutor executor = new ToolExecutor(drivers, schemaValidator);
 * 
 * // Execute tool
 * ToolManifest manifest = repository.loadTool(\"web.firecrawl\");
 * JsonNode input = mapper.readTree(\"{\\\"url\\\":\\\"https://example.com\\\"}\");
 * RunResult result = executor.execute(manifest, input);
 * }</pre>
 * 
 * @see ToolDriver
 * @see ToolManifest
 * @since 0.1.0
 */
public class ToolExecutor {
    private final Map<String, ToolDriver> drivers = new HashMap<>();
    private final SchemaValidator schemaValidator;

    /**
     * Create a new ToolExecutor with given drivers.
     * 
     * <p>Drivers are automatically registered by their type() identifier.
     * Duplicate types will overwrite previous registrations.</p>
     * 
     * @param drivers the list of tool drivers to register
     * @param schemaValidator the JSON schema validator
     */
    public ToolExecutor(List<ToolDriver> drivers, SchemaValidator schemaValidator) {
        for (ToolDriver driver : drivers) {
            this.drivers.put(driver.type(), driver);
        }
        this.schemaValidator = schemaValidator;
    }

    /**
     * Execute a tool with given manifest and input.
     * 
     * <p>Performs complete validation and execution flow:</p>
     * <ol>
     *   <li>Validates input against manifest's input schema</li>
     *   <li>Routes to appropriate driver based on manifest type</li>
     *   <li>Executes tool via driver</li>
     *   <li>Validates output against manifest's output schema</li>
     * </ol>
     * 
     * @param manifest the tool manifest with type and configuration
     * @param input the input data to pass to the tool
     * @return RunResult with success status and output, or failure with error message
     */
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

