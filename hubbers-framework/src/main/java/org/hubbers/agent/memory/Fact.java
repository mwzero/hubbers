package org.hubbers.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a fact extracted from or stored in a conversation.
 */
public class Fact {
    private final String key;
    private final JsonNode value;
    private final long timestamp;

    public Fact(String key, JsonNode value, long timestamp) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
    }

    public String getKey() {
        return key;
    }

    public JsonNode getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Fact{key='" + key + "', value=" + value + ", timestamp=" + timestamp + "}";
    }
}
