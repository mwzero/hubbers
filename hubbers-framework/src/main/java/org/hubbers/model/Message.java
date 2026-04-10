package org.hubbers.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a message in a conversation.
 * Used for multi-turn conversations and conversation history.
 */
public class Message {
    private final String role;  // "system", "user", "assistant", "tool"
    private final String content;
    private final String toolCallId;  // For tool response messages
    private final String name;  // For tool messages

    private Message(String role, String content, String toolCallId, String name) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.name = name;
    }

    public static Message system(String content) {
        return new Message("system", content, null, null);
    }

    public static Message user(String content) {
        return new Message("user", content, null, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null, null);
    }

    public static Message tool(String toolCallId, String name, String content) {
        return new Message("tool", content, toolCallId, name);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Message{role='" + role + "', content='" + content + "'}";
    }
}
