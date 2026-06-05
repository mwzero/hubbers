package org.hubbers.agent.memory;

/**
 * Platform-level conversation memory interface.
 * Extends the platform-agnostic react interface so that all platform implementations
 * ({@link FileSystemConversationStore}, {@link InMemoryConversationStore}) automatically
 * satisfy {@link org.hubbers.react.memory.ConversationMemory} as well.
 *
 * @deprecated Use {@link org.hubbers.react.memory.ConversationMemory} directly.
 */
@Deprecated(since = "0.3.0", forRemoval = true)
public interface ConversationMemory extends org.hubbers.react.memory.ConversationMemory {
    // All methods inherited from org.hubbers.react.memory.ConversationMemory
}
