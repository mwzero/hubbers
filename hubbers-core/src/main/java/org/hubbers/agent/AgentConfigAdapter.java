package org.hubbers.agent;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.agent.Instructions;
import org.hubbers.manifest.agent.ModelConfig;
import org.hubbers.react.AgentConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts a platform {@link AgentManifest} to a lean {@link AgentConfig}
 * understood by {@link org.hubbers.react.HubberAgentLoop}.
 *
 * <p>This adapter is the single seam between the manifest-rich platform and
 * the platform-free {@code hubbers-react} executor.</p>
 */
public final class AgentConfigAdapter {

    private AgentConfigAdapter() {}

    /**
     * Converts the given {@link AgentManifest} to an {@link AgentConfig}.
     *
     * <p>The system prompt is fully built here (including output schema hint) so that
     * {@code HubberAgentLoop} does not need to know about manifest structures.</p>
     *
     * @param manifest the agent manifest (must not be null)
     * @return a lean {@code AgentConfig} ready for injection into {@code HubberAgentLoop}
     */
    public static AgentConfig from(AgentManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");

        ModelConfig model = manifest.getModel();
        String modelName = model != null ? model.getName() : null;
        String providerName = model != null ? model.getProvider() : null;
        Double temperature = model != null ? model.getTemperature() : null;
        Boolean think = model != null ? model.getThink() : null;

        Instructions instructions = manifest.getInstructions();
        String systemPrompt = buildSystemPrompt(manifest);
        String userPromptTemplate = instructions != null ? instructions.getUserPrompt() : null;

        List<String> artifactNames = collectArtifactNames(manifest);

        int maxIterations = 0;
        long timeoutMs = 0L;
        if (manifest.getConfig() != null) {
            Object maxIter = manifest.getConfig().get("max_iterations");
            if (maxIter instanceof Number n) {
                maxIterations = n.intValue();
            }
            Object timeout = manifest.getConfig().get("timeout_seconds");
            if (timeout instanceof Number n) {
                timeoutMs = n.longValue() * 1000L;
            }
        }

        boolean simpleMode = isSimpleMode(manifest);

        String name = manifest.getAgent() != null ? manifest.getAgent().getName() : "unknown";

        return new AgentConfig(name, systemPrompt, userPromptTemplate, modelName, providerName,
                temperature, think, artifactNames, maxIterations, timeoutMs, simpleMode);
    }

    private static String buildSystemPrompt(AgentManifest manifest) {
        Instructions instructions = manifest.getInstructions();
        if (instructions == null) return "";
        String base = instructions.getSystemPrompt();
        if (base == null) return "";
        if (manifest.getOutput() != null && manifest.getOutput().getSchema() != null) {
            try {
                String schema = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(manifest.getOutput().getSchema());
                return base + "\nReturn ONLY valid JSON that matches this output schema: " + schema;
            } catch (Exception e) {
                return base;
            }
        }
        return base;
    }

    private static List<String> collectArtifactNames(AgentManifest manifest) {
        List<String> names = new ArrayList<>();

        if (manifest.getConfig() != null) {
            Object toolsConfig = manifest.getConfig().get("tools");
            if (toolsConfig instanceof List<?> list) {
                list.stream().filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(names::add);
            }
            Object skillsConfig = manifest.getConfig().get("skills");
            if (skillsConfig instanceof List<?> list) {
                list.stream().filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(n -> !names.contains(n))
                        .forEach(names::add);
            }
        }

        if (names.isEmpty() && manifest.getTools() != null) {
            names.addAll(manifest.getTools());
        }

        return names;
    }

    private static boolean isSimpleMode(AgentManifest manifest) {
        if (manifest.getMode() != null) {
            return "simple".equalsIgnoreCase(manifest.getMode().trim());
        }
        Object maxIter = manifest.getConfig() != null
                ? manifest.getConfig().get("max_iterations") : null;
        return maxIter instanceof Number n && n.intValue() == 1;
    }
}
