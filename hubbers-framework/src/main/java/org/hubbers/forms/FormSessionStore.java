package org.hubbers.forms;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for form sessions.
 * In production, this could be backed by a database or persistent storage.
 */
public class FormSessionStore {
    
    private final Map<String, FormSession> sessions = new ConcurrentHashMap<>();
    
    /**
     * Stores a form session.
     */
    public void put(String sessionId, FormSession session) {
        sessions.put(sessionId, session);
    }
    
    /**
     * Retrieves a form session by ID.
     */
    public FormSession get(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Removes a form session.
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
    
    /**
     * Checks if a session exists.
     */
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
    
    /**
     * Gets all sessions (for debugging/admin).
     */
    public Map<String, FormSession> getAll() {
        return new ConcurrentHashMap<>(sessions);
    }
    
    /**
     * Clears all sessions.
     */
    public void clear() {
        sessions.clear();
    }
}
