package org.hubbers.model;

public interface ModelProvider {
    String providerName();
    ModelResponse generate(ModelRequest request);
}
