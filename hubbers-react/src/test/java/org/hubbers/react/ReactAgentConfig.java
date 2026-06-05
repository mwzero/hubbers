package org.hubbers.react;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Typed representation of {@code agent.yaml} for a ReAct agent.
 *
 * <pre>
 * agent:
 *   name: hubber.react
 * model:
 *   name: qwen2.5-3b-instruct-q8_0.gguf
 * config:
 *   max_iterations: 5
 * prompts:
 *   soul:        system-prompt.md
 *   tools:       tools.md
 *   memory:      memory.md
 *   observation: observation-prompt.md
 *   reflection:  reflection-prompt.md
 *   rejection:   rejection-prompt.md
 * tool_rag:
 *   enabled: true
 *   top_k:   5
 *   discovery_prompt: discovery-prompt.md
 * </pre>
 *
 * <p>The {@code resourceBase} component is not deserialized from YAML — it is
 * computed from the actual classpath URL of the loaded file and used by
 * {@link #resolvePrompt(String)} to avoid any hardcoded paths.</p>
 */
public record ReactAgentConfig(
        AgentMeta agent,
        ModelConfig model,
        AgentConfig config,
        PromptsConfig prompts,
        ToolRagConfig toolRag,
        String resourceBase) {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /**
     * Jackson creator — used during YAML deserialization only.
     * {@code resourceBase} is excluded here and injected afterwards by
     * {@link #fromResource(String)}.
     */
    @JsonCreator
    public static ReactAgentConfig fromYaml(
            @JsonProperty("agent")    AgentMeta agent,
            @JsonProperty("model")    ModelConfig model,
            @JsonProperty("config")   AgentConfig config,
            @JsonProperty("prompts")  PromptsConfig prompts,
            @JsonProperty("tool_rag") ToolRagConfig toolRag) {
        return new ReactAgentConfig(agent, model, config, prompts, toolRag, "");
    }

    /**
     * Loads and parses an {@code agent.yaml} from the given classpath resource,
     * deriving the base directory from the resource URL so that
     * {@link #resolvePrompt(String)} needs no hardcoded paths.
     *
     * @param resourcePath classpath resource path (e.g. {@code "/org/hubbers/react/agent.yaml"})
     */
    public static ReactAgentConfig fromResource(String resourcePath) throws IOException {
        URL url = ReactAgentConfig.class.getResource(resourcePath);
        if (url == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        String base = resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1);
        try (InputStream is = url.openStream()) {
            ReactAgentConfig tmp = YAML.readValue(is, ReactAgentConfig.class);
            return new ReactAgentConfig(tmp.agent(), tmp.model(), tmp.config(), tmp.prompts(), tmp.toolRag(), base);
        }
    }

    /**
     * Resolves a prompt filename (relative to the same classpath directory as
     * {@code agent.yaml}) to an absolute {@link Path}.
     * <p>The returned path points into {@code target/test-classes} (the compiled
     * classpath). For read-only prompt files this is fine. For mutable files
     * (e.g. {@code memory.md}) use {@link #resolveWritable(String)} instead.
     */
    public Path resolvePrompt(String filename) throws IOException {
        URL url = ReactAgentConfig.class.getResource(resourceBase + filename);
        if (url == null) {
            throw new IOException("Prompt resource not found: " + resourceBase + filename);
        }
        try {
            return Path.of(url.toURI()).toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for prompt: " + filename, e);
        }
    }

    /**
     * Resolves a mutable file (e.g. {@code memory.md}) to its <em>source</em> path
     * in {@code src/test/resources}, so writes survive a Maven clean and are
     * immediately visible in the IDE without recompiling.
     *
     * <p>Remaps {@code .../target/test-classes/...} to
     * {@code .../src/test/resources/...}.  If the source tree cannot be located
     * (e.g. running from a JAR), falls back to {@link #resolvePrompt(String)}.
     */
    public Path resolveWritable(String filename) throws IOException {
        Path compiled = resolvePrompt(filename);
        Path remapped = remapToSource(compiled);
        return remapped != null ? remapped : compiled;
    }

    /**
     * Attempts to remap a path inside {@code target/test-classes} to the
     * corresponding path inside {@code src/test/resources}.
     *
     * @return the source path if it exists, otherwise {@code null}
     */
    private static Path remapToSource(Path compiled) {
        // Walk up looking for a segment named "target"
        Path cursor = compiled;
        while (cursor != null) {
            if ("target".equals(cursor.getFileName() != null ? cursor.getFileName().toString() : "")) {
                Path moduleRoot = cursor.getParent();
                if (moduleRoot != null) {
                    // Strip the "target/test-classes" prefix and re-root under src/test/resources
                    Path relative = cursor.relativize(compiled);          // e.g. test-classes/org/...
                    int segments  = relative.getNameCount();
                    if (segments > 1) {
                        Path underResources = relative.subpath(1, segments); // strip "test-classes"
                        Path source = moduleRoot.resolve("src").resolve("test").resolve("resources")
                                .resolve(underResources);
                        if (source.toFile().exists()) {
                            return source.toAbsolutePath();
                        }
                    }
                }
                break;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Nested config records
    // -------------------------------------------------------------------------

    public record AgentMeta(String name, String version, String description) {}

    public record ModelConfig(String name) {}

    public record AgentConfig(@JsonProperty("max_iterations") int maxIterations) {}

    public record PromptsConfig(
            String soul,
            String tools,
            String memory,
            String observation,
            String reflection,
            String rejection) {}

    public record ToolRagConfig(
            boolean enabled,
            @JsonProperty("top_k") int topK,
            @JsonProperty("discovery_prompt") String discoveryPrompt) {

        /** Returns a disabled default instance used when the section is absent from the YAML. */
        public static ToolRagConfig disabled() {
            return new ToolRagConfig(false, 5, null);
        }
    }

    /** Returns the tool_rag config, falling back to a disabled instance if absent from the YAML. */
    public ToolRagConfig toolRagOrDefault() {
        return toolRag != null ? toolRag : ToolRagConfig.disabled();
    }
}
