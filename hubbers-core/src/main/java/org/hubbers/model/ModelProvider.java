package org.hubbers.model;

import org.hubbers.react.model.ModelRequest;
import org.hubbers.react.model.ModelResponse;

/**
 * Platform-level model provider interface.
 * Extends {@link org.hubbers.react.model.ModelProvider} and adds a {@link #providerName()} method
 * for routing within the {@link ModelProviderRegistry}.
 */
public interface ModelProvider extends org.hubbers.react.model.ModelProvider {
    String providerName();

    @Override
    ModelResponse generate(ModelRequest request);
}
