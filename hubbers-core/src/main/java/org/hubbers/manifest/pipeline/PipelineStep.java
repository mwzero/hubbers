package org.hubbers.manifest.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hubbers.forms.FormDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class PipelineStep {
    private String id;
    private String agent;
    private String tool;
    @JsonProperty("input_mapping")
    private Map<String, String> inputMapping;
    private FormDefinition form; // Form to show during this step (human-in-the-loop)

    /** Optional expression evaluated against pipeline state; step is skipped when false. */
    private String condition;

    /** Optional retry configuration for transient failures. */
    private RetryConfig retry;

    /** Optional error handling strategy when the step fails after retries. */
    @JsonProperty("on_error")
    private ErrorHandler onError;

    /**
     * Returns a fluent builder for constructing a {@code PipelineStep} from code.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link PipelineStep}.
     *
     * <pre>{@code
     * PipelineStep step = PipelineStep.builder()
     *     .id("research_task")
     *     .agent("Senior Research Analyst")
     *     .input("query", "Analyze the repository for innovations.")
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private String id;
        private String agent;
        private String tool;
        private final Map<String, String> inputMapping = new LinkedHashMap<>();
        private String condition;
        private RetryConfig retry;
        private ErrorHandler onError;

        private Builder() {}

        /** Sets the step identifier (required). */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /** Assigns an agent (by name) to this step. Mutually exclusive with {@link #tool}. */
        public Builder agent(String agentName) {
            this.agent = agentName;
            return this;
        }

        /** Assigns a tool (by name) to this step. Mutually exclusive with {@link #agent}. */
        public Builder tool(String toolName) {
            this.tool = toolName;
            return this;
        }

        /** Adds a single input mapping entry. Can be called multiple times. */
        public Builder input(String key, String value) {
            this.inputMapping.put(key, value);
            return this;
        }

        /** Sets the entire input mapping at once (replaces any previously added entries). */
        public Builder inputMapping(Map<String, String> mapping) {
            this.inputMapping.clear();
            this.inputMapping.putAll(mapping);
            return this;
        }

        /** Sets a condition expression; the step is skipped when it evaluates to false. */
        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        /** Sets the retry configuration for transient failures. */
        public Builder retry(RetryConfig retry) {
            this.retry = retry;
            return this;
        }

        /** Sets the error handling strategy. */
        public Builder onError(ErrorHandler onError) {
            this.onError = onError;
            return this;
        }

        /**
         * Builds the {@link PipelineStep}.
         *
         * @throws IllegalStateException if {@code id} is not set
         */
        public PipelineStep build() {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("PipelineStep requires an id");
            }
            PipelineStep step = new PipelineStep();
            step.setId(id);
            step.setAgent(agent);
            step.setTool(tool);
            if (!inputMapping.isEmpty()) {
                step.setInputMapping(new LinkedHashMap<>(inputMapping));
            }
            step.setCondition(condition);
            step.setRetry(retry);
            step.setOnError(onError);
            return step;
        }
    }
}
