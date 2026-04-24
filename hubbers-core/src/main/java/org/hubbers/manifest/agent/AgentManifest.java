package org.hubbers.manifest.agent;

import lombok.Data;
import org.hubbers.forms.FormTrigger;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.ExampleDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentManifest {
    
    private Metadata agent;
    private ModelConfig model;
    private Instructions instructions;
    private InputDefinition input;
    private OutputDefinition output;
    private Map<String, Object> config = new HashMap<>();
    private List<String> tools = new ArrayList<>();
    private List<ExampleDefinition> examples = new ArrayList<>();
    private FormTrigger forms;

    /**
     * Execution mode for this agent.
     * <ul>
     *   <li>{@code "simple"} — single LLM call, no tool loop, no ReAct iterations</li>
     *   <li>{@code "agentic"} — full ReAct loop with tool calling (default when absent)</li>
     * </ul>
     */
    private String mode;

    /**
     * Returns a fluent builder for constructing an {@code AgentManifest} from code.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link AgentManifest}.
     *
     * <pre>{@code
     * AgentManifest researcher = AgentManifest.builder()
     *     .name("Senior Research Analyst")
     *     .instructions("Find 3 key AI innovations.")
     *     .tools("serper.search", "file.read")
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private String name;
        private String version;
        private String description;
        private ModelConfig model;
        private String systemPrompt;
        private String userPrompt;
        private final List<String> tools = new ArrayList<>();

        private Builder() {}

        /** Sets the agent name (required). */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** Sets the agent version. */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /** Sets the agent description. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Sets the model configuration. */
        public Builder model(ModelConfig model) {
            this.model = model;
            return this;
        }

        /** Sets the system prompt instructions. */
        public Builder instructions(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return this;
        }

        /** Adds one or more tool names (by ID) to the agent. */
        public Builder tools(String... toolNames) {
            this.tools.addAll(Arrays.asList(toolNames));
            return this;
        }

        /**
         * Builds the {@link AgentManifest}.
         *
         * @throws IllegalStateException if {@code name} is not set
         */
        public AgentManifest build() {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("AgentManifest requires a name");
            }
            AgentManifest m = new AgentManifest();
            Metadata meta = new Metadata();
            meta.setName(name);
            meta.setVersion(version);
            meta.setDescription(description);
            m.setAgent(meta);
            m.setModel(model);
            if (systemPrompt != null || userPrompt != null) {
                m.setInstructions(new Instructions(systemPrompt, userPrompt));
            }
            m.setTools(new ArrayList<>(tools));
            return m;
        }
    }
}
