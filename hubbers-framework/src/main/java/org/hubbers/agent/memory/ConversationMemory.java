package org.hubbers.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.model.Message;

import java.util.List;

/**
 * Interface for storing and retrieving conversation history and facts.
 */
public interface ConversationMemory {
    
    /**
     * Save a message to the conversation history.
     */
    void saveMessage(String conversationId, Message message);
    
    /**
     * Load the full conversation history.
     */
    List<Message> loadHistory(String conversationId);
    
    /**
     * Save a fact extracted from the conversation.
     */
    void saveFact(String conversationId, String key, JsonNode value);
    
    /**
     * Retrieve a specific fact by key.
     */
    JsonNode getFact(String conversationId, String key);
    
    /**
     * Search facts by query (semantic search if supported).
     */
    List<Fact> searchFacts(String conversationId, String query);
    
    /**
     * Clear conversation history (for cleanup).
     */
    void clearConversation(String conversationId);
}
