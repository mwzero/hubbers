package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.manifest.common.SchemaDefinition;
import org.hubbers.validation.SchemaValidator;
import org.hubbers.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Base class for all artifact executors providing common validation logic.
 * 
 * <p>This abstract class eliminates code duplication across executor implementations
 * by providing reusable input/output validation methods. All executors that validate
 * against JSON schemas should extend this class.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Consistent validation error handling</li>
 *   <li>Input schema validation</li>
 *   <li>Output schema validation</li>
 *   <li>Fail-fast validation with Optional pattern</li>
 * </ul>
 * </p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * public class MyExecutor extends BaseExecutor {
 *     public MyExecutor(SchemaValidator validator, ObjectMapper mapper) {
 *         super(validator, mapper);
 *     }
 *     
 *     public RunResult execute(MyManifest manifest, JsonNode input) {
 *         // Validate input
 *         Optional<RunResult> validationError = validateInput(input, manifest.getInput());
 *         if (validationError.isPresent()) {
 *             return validationError.get();
 *         }
 *         
 *         // Execute logic...
 *         JsonNode output = doExecution(input);
 *         
 *         // Validate output
 *         validationError = validateOutput(output, manifest.getOutput());
 *         if (validationError.isPresent()) {
 *             return validationError.get();
 *         }
 *         
 *         return RunResult.success(output);
 *     }
 * }
 * }</pre>
 * 
 * @since 0.1.0
 */
public abstract class BaseExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(BaseExecutor.class);
    
    protected final SchemaValidator schemaValidator;
    protected final ObjectMapper mapper;
    
    /**
     * Create a new base executor.
     * 
     * @param schemaValidator the JSON schema validator
     * @param mapper the JSON object mapper
     */
    protected BaseExecutor(SchemaValidator schemaValidator, ObjectMapper mapper) {
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
    }
    
    /**
     * Validate input data against a schema definition.
     * 
     * <p>Returns an error RunResult wrapped in Optional if validation fails,
     * or empty Optional if validation succeeds. This allows fail-fast pattern:</p>
     * 
     * <pre>{@code
     * Optional<RunResult> error = validateInput(input, schema);
     * if (error.isPresent()) {
     *     return error.get(); // Return early with validation error
     * }
     * // Continue with execution...
     * }</pre>
     * 
     * @param input the input data to validate
     * @param schemaDefinition the schema definition containing validation rules
     * @return Optional containing error RunResult if validation fails, empty otherwise
     */
    protected Optional<RunResult> validateInput(JsonNode input, SchemaDefinition schemaDefinition) {
        if (schemaDefinition == null) {
            log.debug("No input schema defined, skipping validation");
            return Optional.empty();
        }
        
        ValidationResult result = schemaValidator.validate(input, schemaDefinition);
        if (!result.isValid()) {
            String errorMessage = String.join(", ", result.getErrors());
            log.warn("Input validation failed: {}", errorMessage);
            return Optional.of(RunResult.failed("Input validation failed: " + errorMessage));
        }
        
        log.debug("Input validation passed");
        return Optional.empty();
    }
    
    /**
     * Validate output data against a schema definition.
     * 
     * <p>Returns an error RunResult wrapped in Optional if validation fails,
     * or empty Optional if validation succeeds.</p>
     * 
     * @param output the output data to validate
     * @param schemaDefinition the schema definition containing validation rules
     * @return Optional containing error RunResult if validation fails, empty otherwise
     */
    protected Optional<RunResult> validateOutput(JsonNode output, SchemaDefinition schemaDefinition) {
        if (schemaDefinition == null) {
            log.debug("No output schema defined, skipping validation");
            return Optional.empty();
        }
        
        ValidationResult result = schemaValidator.validate(output, schemaDefinition);
        if (!result.isValid()) {
            String errorMessage = String.join(", ", result.getErrors());
            log.warn("Output validation failed: {}", errorMessage);
            return Optional.of(RunResult.failed("Output validation failed: " + errorMessage));
        }
        
        log.debug("Output validation passed");
        return Optional.empty();
    }
    
    /**
     * Convenience method to validate input with direct schema definition.
     * 
     * @param input the input data to validate
     * @param schema the schema definition
     * @return Optional containing error RunResult if validation fails, empty otherwise
     */
    protected Optional<RunResult> validateInputWithSchema(JsonNode input, SchemaDefinition schema) {
        if (schema == null) {
            log.debug("No schema provided, skipping validation");
            return Optional.empty();
        }
        
        ValidationResult result = schemaValidator.validate(input, schema);
        if (!result.isValid()) {
            String errorMessage = String.join(", ", result.getErrors());
            log.warn("Input validation failed: {}", errorMessage);
            return Optional.of(RunResult.failed("Input validation failed: " + errorMessage));
        }
        
        log.debug("Input validation passed");
        return Optional.empty();
    }
    
    /**
     * Convenience method to validate output with direct schema definition.
     * 
     * @param output the output data to validate
     * @param schema the schema definition
     * @return Optional containing error RunResult if validation fails, empty otherwise
     */
    protected Optional<RunResult> validateOutputWithSchema(JsonNode output, SchemaDefinition schema) {
        if (schema == null) {
            log.debug("No schema provided, skipping validation");
            return Optional.empty();
        }
        
        ValidationResult result = schemaValidator.validate(output, schema);
        if (!result.isValid()) {
            String errorMessage = String.join(", ", result.getErrors());
            log.warn("Output validation failed: {}", errorMessage);
            return Optional.of(RunResult.failed("Output validation failed: " + errorMessage));
        }
        
        log.debug("Output validation passed");
        return Optional.empty();
    }
    
    /**
     * Create execution metadata with timing information.
     * 
     * @param startTime the execution start time in milliseconds
     * @param details optional details string (e.g., "model=gpt-4, tokens=150")
     * @return ExecutionMetadata with start/end times and details
     */
    protected ExecutionMetadata createMetadata(long startTime, String details) {
        long endTime = System.currentTimeMillis();
        ExecutionMetadata metadata = new ExecutionMetadata();
        metadata.setStartedAt(startTime);
        metadata.setEndedAt(endTime);
        if (details != null) {
            metadata.setDetails(details);
        }
        return metadata;
    }
}
