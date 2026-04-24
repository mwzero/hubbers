package org.hubbers.model;

import java.util.function.Consumer;

/**
 * Extension of {@link ModelProvider} that supports streaming token-by-token output.
 *
 * <p>Providers implementing this interface can deliver tokens incrementally via a consumer
 * callback, enabling real-time streaming to clients via SSE or WebSocket.</p>
 *
 * @since 0.1.0
 */
public interface StreamingModelProvider extends ModelProvider {

    /**
     * Generate a response with streaming token delivery.
     *
     * <p>Each generated token (or token chunk) is passed to the {@code tokenConsumer}
     * as it becomes available. The final {@link ModelResponse} is returned after
     * streaming completes with accumulated content and metadata.</p>
     *
     * @param request the model request
     * @param tokenConsumer callback invoked for each token chunk
     * @return the complete model response after streaming finishes
     */
    ModelResponse generateStreaming(ModelRequest request, Consumer<String> tokenConsumer);
}
