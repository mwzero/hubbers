package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.util.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Smart model router that prefers local LLMs (Ollama, llama.cpp) over cloud providers
 * to minimize token costs. Periodically checks Ollama availability and model list.
 *
 * <p>Routing strategy:
 * <ol>
 *   <li>If the manifest specifies a provider+model, use it directly</li>
 *   <li>If a model override is requested, resolve which provider has it</li>
 *   <li>Otherwise, prefer local (Ollama &rarr; llama.cpp) over cloud (OpenAI &rarr; Anthropic)</li>
 * </ol>
 *
 * @since 0.1.0
 */
public class ModelRouter {
    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    /** How often (ms) to refresh the Ollama model list. */
    private static final long REFRESH_INTERVAL_MS = 60_000;

    private final ModelProviderRegistry registry;
    private final String ollamaBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private final CopyOnWriteArrayList<OllamaModelInfo> ollamaModels = new CopyOnWriteArrayList<>();
    private final AtomicLong lastRefreshMs = new AtomicLong(0);
    private volatile boolean ollamaAvailable = false;

    // Token usage accumulator: provider -> { promptTokens, completionTokens }
    private final ConcurrentHashMap<String, long[]> usageByProvider = new ConcurrentHashMap<>();

    /**
     * Lightweight snapshot of an Ollama model entry.
     *
     * @param name   full model tag (e.g. "qwen3:4b")
     * @param sizeBytes model size in bytes
     */
    public record OllamaModelInfo(String name, long sizeBytes) {}

    /**
     * Creates a ModelRouter.
     *
     * @param registry       model provider registry
     * @param ollamaBaseUrl  Ollama base URL (e.g. "http://localhost:11434")
     * @param httpClient     HTTP client for health/model checks
     */
    public ModelRouter(ModelProviderRegistry registry, String ollamaBaseUrl, HttpClient httpClient) {
        this.registry = registry;
        this.ollamaBaseUrl = ollamaBaseUrl != null ? ollamaBaseUrl : "http://localhost:11434";
        this.httpClient = httpClient;
        this.mapper = JacksonFactory.jsonMapper();
    }

    /**
     * Returns the best provider for a given request, preferring local models.
     *
     * @param preferredProvider  provider from manifest (may be null)
     * @param modelOverride      model name override from user/UI (may be null)
     * @return the resolved ModelProvider
     */
    public ModelProvider route(String preferredProvider, String modelOverride) {
        // If an explicit override has a "provider:model" format, split it
        if (modelOverride != null && modelOverride.contains(":")) {
            String[] parts = modelOverride.split(":", 2);
            try {
                return registry.get(parts[0]);
            } catch (IllegalArgumentException e) {
                log.debug("Override provider '{}' not registered, falling through", parts[0]);
            }
        }

        // If manifest specifies a provider, honour it
        if (preferredProvider != null && !preferredProvider.isBlank()) {
            try {
                return registry.get(preferredProvider);
            } catch (IllegalArgumentException e) {
                log.warn("Configured provider '{}' not available, falling back", preferredProvider);
            }
        }

        // Local-first: Ollama → llama.cpp → openai → anthropic
        refreshOllamaModelsIfStale();
        if (ollamaAvailable) {
            try { return registry.get("ollama"); } catch (IllegalArgumentException ignored) {}
        }
        for (String fallback : List.of("llama.cpp", "openai", "anthropic")) {
            try { return registry.get(fallback); } catch (IllegalArgumentException ignored) {}
        }

        throw new IllegalStateException("No model providers available");
    }

    /**
     * Returns the current list of locally available Ollama models.
     * Triggers a refresh if stale.
     *
     * @return unmodifiable list of Ollama models
     */
    public List<OllamaModelInfo> getOllamaModels() {
        refreshOllamaModelsIfStale();
        return Collections.unmodifiableList(ollamaModels);
    }

    /**
     * Returns whether Ollama is reachable.
     *
     * @return true if Ollama responded to the last check
     */
    public boolean isOllamaAvailable() {
        refreshOllamaModelsIfStale();
        return ollamaAvailable;
    }

    /**
     * Records token usage from a model response.
     *
     * @param providerName  the provider that served the request
     * @param response      the model response containing token counts
     */
    public void recordUsage(String providerName, ModelResponse response) {
        usageByProvider.compute(providerName, (k, v) -> {
            if (v == null) v = new long[]{0, 0};
            v[0] += response.getPromptTokens();
            v[1] += response.getCompletionTokens();
            return v;
        });
    }

    /**
     * Returns token usage summary by provider.
     *
     * @return map of provider name to usage stats
     */
    public Map<String, long[]> getUsageByProvider() {
        return Collections.unmodifiableMap(usageByProvider);
    }

    /**
     * Returns total tokens consumed across all providers.
     *
     * @return total token count
     */
    public long getTotalTokens() {
        return usageByProvider.values().stream()
                .mapToLong(v -> v[0] + v[1])
                .sum();
    }

    /**
     * Refreshes the Ollama model list if more than {@link #REFRESH_INTERVAL_MS} has elapsed.
     */
    private void refreshOllamaModelsIfStale() {
        long now = System.currentTimeMillis();
        long last = lastRefreshMs.get();
        if (now - last < REFRESH_INTERVAL_MS) return;
        if (!lastRefreshMs.compareAndSet(last, now)) return; // another thread is refreshing

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode body = mapper.readTree(response.body());
                JsonNode modelsNode = body.path("models");
                if (modelsNode.isArray()) {
                    ollamaModels.clear();
                    for (JsonNode m : modelsNode) {
                        ollamaModels.add(new OllamaModelInfo(
                                m.path("name").asText(),
                                m.path("size").asLong(0)
                        ));
                    }
                }
                ollamaAvailable = true;
                log.debug("Ollama model refresh: {} models available", ollamaModels.size());
            } else {
                ollamaAvailable = false;
                log.debug("Ollama returned status {}", response.statusCode());
            }
        } catch (Exception e) {
            ollamaAvailable = false;
            log.debug("Ollama not reachable: {}", e.getMessage());
        }
    }
}
