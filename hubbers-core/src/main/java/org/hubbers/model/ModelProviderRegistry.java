package org.hubbers.model;

import org.hubbers.react.model.ModelRequest;
import org.hubbers.react.model.ModelResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of named {@link ModelProvider} instances.
 *
 * <p>Implements {@link org.hubbers.react.model.ModelProvider} so it can be injected
 * directly into {@link org.hubbers.react.HubberAgentLoop}. Routing is performed by
 * reading {@code request.getProvider()} and delegating to the matching provider.
 * Falls back to the first registered provider if the requested provider name is absent
 * or blank.</p>
 */
public class ModelProviderRegistry implements org.hubbers.react.model.ModelProvider {

    private final Map<String, ModelProvider> providers = new HashMap<>();
    private ModelProvider defaultProvider;

    public ModelProviderRegistry(List<ModelProvider> providerList) {
        for (ModelProvider provider : providerList) {
            providers.put(provider.providerName(), provider);
        }
        if (!providerList.isEmpty()) {
            defaultProvider = providerList.get(0);
        }
    }

    /**
     * Get a provider by name.
     *
     * @param providerName the provider name
     * @return the provider
     * @throws IllegalArgumentException if the provider is not found
     */
    public ModelProvider get(String providerName) {
        ModelProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Model provider not found: " + providerName);
        }
        return provider;
    }

    /**
     * Routes the request to the provider specified by {@link ModelRequest#getProvider()}.
     * Falls back to the first registered provider if the provider name is absent or unknown.
     *
     * @param request the model request (may have a {@code provider} hint)
     * @return the model response
     */
    @Override
    public ModelResponse generate(ModelRequest request) {
        String providerName = request.getProvider();
        ModelProvider target = (providerName != null && !providerName.isBlank())
                ? providers.getOrDefault(providerName, defaultProvider)
                : defaultProvider;
        if (target == null) {
            throw new IllegalStateException("No model providers registered");
        }
        return target.generate(request);
    }
}
