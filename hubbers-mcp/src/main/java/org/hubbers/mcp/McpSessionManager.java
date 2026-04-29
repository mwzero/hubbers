package org.hubbers.mcp;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages stateful MCP sessions so external chat UIs can maintain
 * conversation context across multiple {@code tools/call} invocations.
 *
 * <p>Each session tracks a conversation ID and metadata, allowing
 * agent tool calls from the same MCP client to share conversation memory.</p>
 *
 * @since 0.1.0
 */
@Slf4j
public class McpSessionManager {

    /** Maximum age in minutes before a session is considered expired. */
    private static final long SESSION_TTL_MINUTES = 60;

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    /**
     * Gets or creates a session for the given session ID.
     * If no session ID is provided, a new session is created.
     *
     * @param sessionId the client-provided session ID, or null to create a new one
     * @return the active session
     */
    public McpSession getOrCreate(String sessionId) {
        if (sessionId != null && sessions.containsKey(sessionId)) {
            McpSession session = sessions.get(sessionId);
            session.touch();
            log.debug("Reusing MCP session: {}", sessionId);
            return session;
        }

        String newId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        String conversationId = UUID.randomUUID().toString();
        McpSession session = new McpSession(newId, conversationId);
        sessions.put(newId, session);
        log.info("Created new MCP session: {} with conversation: {}", newId, conversationId);
        return session;
    }

    /**
     * Retrieves an existing session by ID.
     *
     * @param sessionId the session ID
     * @return the session, or empty if not found
     */
    public Optional<McpSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Removes expired sessions older than {@link #SESSION_TTL_MINUTES}.
     *
     * @return the number of sessions purged
     */
    public int purgeExpired() {
        Instant cutoff = Instant.now().minusSeconds(SESSION_TTL_MINUTES * 60);
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().getLastActivity().isBefore(cutoff));
        int purged = before - sessions.size();
        if (purged > 0) {
            log.info("Purged {} expired MCP sessions", purged);
        }
        return purged;
    }

    /**
     * Returns the number of active sessions.
     *
     * @return active session count
     */
    public int activeCount() {
        return sessions.size();
    }

    /**
     * Represents a single MCP client session with conversation state.
     */
    public static class McpSession {
        private final String sessionId;
        private final String conversationId;
        private final Instant createdAt;
        private Instant lastActivity;

        McpSession(String sessionId, String conversationId) {
            this.sessionId = sessionId;
            this.conversationId = conversationId;
            this.createdAt = Instant.now();
            this.lastActivity = Instant.now();
        }

        /** @return the session identifier */
        public String getSessionId() { return sessionId; }

        /** @return the conversation ID used for agent memory */
        public String getConversationId() { return conversationId; }

        /** @return when this session was created */
        public Instant getCreatedAt() { return createdAt; }

        /** @return when this session was last used */
        public Instant getLastActivity() { return lastActivity; }

        /** Updates the last-activity timestamp. */
        void touch() { this.lastActivity = Instant.now(); }
    }
}
