package org.hubbers.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ConversationMemory.
 * Suitable for development and testing. Data is lost on restart.
 */
public class InMemoryConversationStore implements ConversationMemory {
    
    private final Map<String, List<Message>> conversations;
    private final Map<String, Map<String, Fact>> facts;

    public InMemoryConversationStore() {
        this.conversations = new ConcurrentHashMap<>();
        this.facts = new ConcurrentHashMap<>();
    }

    @Override
    public void saveMessage(String conversationId, Message message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
    }

    @Override
    public List<Message> loadHistory(String conversationId) {
        return new ArrayList<>(conversations.getOrDefault(conversationId, List.of()));
    }

    @Override
    public void saveFact(String conversationId, String key, JsonNode value) {
        Map<String, Fact> conversationFacts = facts.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>());
        conversationFacts.put(key, new Fact(key, value, System.currentTimeMillis()));
    }

    @Override
    public JsonNode getFact(String conversationId, String key) {
        Map<String, Fact> conversationFacts = facts.get(conversationId);
        if (conversationFacts == null) {
            return null;
        }
        Fact fact = conversationFacts.get(key);
        return fact != null ? fact.getValue() : null;
    }

    @Override
    public List<Fact> searchFacts(String conversationId, String query) {
        Map<String, Fact> conversationFacts = facts.get(conversationId);
        if (conversationFacts == null) {
            return List.of();
        }
        
        // Simple keyword matching for in-memory implementation
        String lowerQuery = query.toLowerCase();
        return conversationFacts.values().stream()
                .filter(fact -> fact.getKey().toLowerCase().contains(lowerQuery) || 
                               fact.getValue().toString().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    @Override
    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        facts.remove(conversationId);
    }
}
