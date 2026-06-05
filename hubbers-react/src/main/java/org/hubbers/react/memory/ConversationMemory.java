package org.hubbers.react.memory;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.react.model.Message;

import java.util.List;

/**
 * Interface for storing and retrieving conversation history and facts.
 *
 * <p>Implementations may use in-memory storage (for tests or simple mode) or
 * file-system / database backends for production multi-turn conversations.</p>
 */
public interface ConversationMemory {

    /**
     * Saves a single message to the conversation history.
     *
     * @param conversationId unique conversation identifier
     * @param message        the message to persist
     */
    void saveMessage(String conversationId, Message message);

    /**
     * Loads the full conversation history for the given conversation.
     *
     * @param conversationId unique conversation identifier
     * @return ordered list of messages (oldest first)
     */
    List<Message> loadHistory(String conversationId);

    /**
     * Saves a fact extracted from the conversation.
     *
     * @param conversationId unique conversation identifier
     * @param key            fact key
     * @param value          fact value as JSON
     */
    void saveFact(String conversationId, String key, JsonNode value);

    /**
     * Retrieves a specific fact by key.
     *
     * @param conversationId unique conversation identifier
     * @param key            fact key
     * @return the fact value, or {@code null} if not found
     */
    JsonNode getFact(String conversationId, String key);

    /**
     * Searches facts by query (semantic search if supported).
     *
     * @param conversationId unique conversation identifier
     * @param query          search query
     * @return matching facts
     */
    List<Fact> searchFacts(String conversationId, String query);

    /**
     * Clears conversation history and facts (for cleanup or reset).
     *
     * @param conversationId unique conversation identifier
     */
    void clearConversation(String conversationId);

    /**
     * Lists all stored conversation IDs.
     *
     * @return list of conversation identifiers
     */
    default List<String> listConversations() {
        return List.of();
    }
}
