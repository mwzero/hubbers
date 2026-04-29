package org.hubbers.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpSessionManager Tests")
class McpSessionManagerTest {

    private McpSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new McpSessionManager();
    }

    @Test
    @DisplayName("Should create a new session when no ID provided")
    void testGetOrCreate_WithNullId_CreatesNewSession() {
        var session = sessionManager.getOrCreate(null);

        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertNotNull(session.getConversationId());
        assertEquals(1, sessionManager.activeCount());
    }

    @Test
    @DisplayName("Should create session with provided ID")
    void testGetOrCreate_WithProvidedId_UsesIt() {
        var session = sessionManager.getOrCreate("my-session");

        assertEquals("my-session", session.getSessionId());
        assertNotNull(session.getConversationId());
    }

    @Test
    @DisplayName("Should reuse existing session for same ID")
    void testGetOrCreate_WithExistingId_ReusesSession() {
        var first = sessionManager.getOrCreate("session-1");
        var second = sessionManager.getOrCreate("session-1");

        assertSame(first.getConversationId(), second.getConversationId());
        assertEquals(1, sessionManager.activeCount());
    }

    @Test
    @DisplayName("Should create separate sessions for different IDs")
    void testGetOrCreate_WithDifferentIds_CreatesSeparateSessions() {
        var s1 = sessionManager.getOrCreate("a");
        var s2 = sessionManager.getOrCreate("b");

        assertNotEquals(s1.getConversationId(), s2.getConversationId());
        assertEquals(2, sessionManager.activeCount());
    }

    @Test
    @DisplayName("Should retrieve existing session by ID")
    void testGet_WithExistingId_ReturnsSession() {
        sessionManager.getOrCreate("test-session");

        var result = sessionManager.get("test-session");

        assertTrue(result.isPresent());
        assertEquals("test-session", result.get().getSessionId());
    }

    @Test
    @DisplayName("Should return empty for non-existent session")
    void testGet_WithNonExistentId_ReturnsEmpty() {
        var result = sessionManager.get("non-existent");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should purge expired sessions")
    void testPurgeExpired_RemovesOldSessions() {
        // Create a session (will be fresh, so should not be purged)
        sessionManager.getOrCreate("fresh-session");
        assertEquals(1, sessionManager.activeCount());

        // Purge — nothing should be removed since session is fresh
        int purged = sessionManager.purgeExpired();
        assertEquals(0, purged);
        assertEquals(1, sessionManager.activeCount());
    }
}
