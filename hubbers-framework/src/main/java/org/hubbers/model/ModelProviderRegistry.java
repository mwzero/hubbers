package org.hubbers.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelProviderRegistry {
    private final Map<String, ModelProvider> providers = new HashMap<>();

    public ModelProviderRegistry(List<ModelProvider> providers) {
        for (ModelProvider provider : providers) {
            this.providers.put(provider.providerName(), provider);
        }
    }

    public ModelProvider get(String providerName) {
        ModelProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Model provider not found: " + providerName);
        }
        return provider;
    }
}
