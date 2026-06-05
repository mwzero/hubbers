package org.hubbers.react;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hubbers.react.memory.ConversationMemory;
import org.hubbers.react.memory.Fact;
import org.hubbers.react.model.Message;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Simple in-memory {@link ConversationMemory} for standalone demo use.
 * Production code should use {@code InMemoryConversationStore} from {@code hubbers-core}.
 */
public class InMemoryMemory implements ConversationMemory {

    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Fact>> facts     = new ConcurrentHashMap<>();

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
        facts.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>())
                .put(key, Fact.builder().key(key).value(value)
                            .timestamp(System.currentTimeMillis()).build());
    }

    @Override
    public JsonNode getFact(String conversationId, String key) {
        Map<String, Fact> cf = facts.get(conversationId);
        if (cf == null) return null;
        Fact fact = cf.get(key);
        return fact != null ? fact.getValue() : null;
    }

    @Override
    public List<Fact> searchFacts(String conversationId, String query) {
        return List.of();
    }

    @Override
    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        facts.remove(conversationId);
    }
}
