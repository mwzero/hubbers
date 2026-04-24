package org.hubbers.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a fact extracted from or stored in a conversation.
 */
@Getter
@Builder
@ToString
public class Fact {
    private final String key;
    private final JsonNode value;
    private final long timestamp;
}
