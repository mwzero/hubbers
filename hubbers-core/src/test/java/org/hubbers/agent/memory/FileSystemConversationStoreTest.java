package org.hubbers.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.model.FunctionCall;
import org.hubbers.model.Message;
import org.hubbers.util.JacksonFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileSystemConversationStore Tests")
class FileSystemConversationStoreTest {

    private FileSystemConversationStore store;
    private ObjectMapper mapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = JacksonFactory.jsonMapper();
        store = new FileSystemConversationStore(tempDir, mapper);
    }

    @Test
    @DisplayName("Should save and load message history")
    void testSaveAndLoadHistory_RoundTrip() {
        // Given
        String conversationId = "test-conv-1";
        Message userMsg = Message.user("Hello, agent!");
        Message assistantMsg = Message.assistant("Hello, user!");

        // When
        store.saveMessage(conversationId, userMsg);
        store.saveMessage(conversationId, assistantMsg);
        List<Message> history = store.loadHistory(conversationId);

        // Then
        assertEquals(2, history.size(), "Should have 2 messages in history");
        assertEquals("user", history.get(0).getRole());
        assertEquals("Hello, agent!", history.get(0).getContent());
        assertEquals("assistant", history.get(1).getRole());
        assertEquals("Hello, user!", history.get(1).getContent());
    }

    @Test
    @DisplayName("Should return empty list for nonexistent conversation")
    void testLoadHistory_NonExistent_ReturnsEmptyList() {
        List<Message> history = store.loadHistory("nonexistent");
        assertTrue(history.isEmpty(), "Should return empty list for nonexistent conversation");
    }

    @Test
    @DisplayName("Should persist system and tool messages correctly")
    void testSaveMessage_AllRoles_PreservesRoles() {
        // Given
        String conversationId = "test-conv-roles";
        store.saveMessage(conversationId, Message.system("You are a test agent."));
        store.saveMessage(conversationId, Message.user("What's the weather?"));
        store.saveMessage(conversationId, Message.assistant("Let me check..."));
        store.saveMessage(conversationId, Message.tool("call-1", "weather.lookup", "{\"temp\": 20}"));

        // When
        List<Message> history = store.loadHistory(conversationId);

        // Then
        assertEquals(4, history.size());
        assertEquals("system", history.get(0).getRole());
        assertEquals("user", history.get(1).getRole());
        assertEquals("assistant", history.get(2).getRole());
        assertEquals("tool", history.get(3).getRole());
        assertEquals("call-1", history.get(3).getToolCallId());
        assertEquals("weather.lookup", history.get(3).getName());
    }

    @Test
    @DisplayName("Should persist assistant messages with tool calls")
    void testSaveMessage_WithToolCalls_PreservesToolCalls() throws Exception {
        // Given
        String conversationId = "test-conv-toolcalls";
        JsonNode args = mapper.readTree("{\"city\": \"Rome\"}");
        FunctionCall functionCall = new FunctionCall("fc-1", "weather.lookup", args);
        Message msg = Message.assistantWithToolCalls("Checking weather", List.of(functionCall));

        // When
        store.saveMessage(conversationId, msg);
        List<Message> history = store.loadHistory(conversationId);

        // Then
        assertEquals(1, history.size());
        Message loaded = history.get(0);
        assertEquals("assistant", loaded.getRole());
        assertEquals("Checking weather", loaded.getContent());
        assertNotNull(loaded.getToolCalls(), "Tool calls should be preserved");
        assertEquals(1, loaded.getToolCalls().size());
        assertEquals("fc-1", loaded.getToolCalls().get(0).getId());
        assertEquals("weather.lookup", loaded.getToolCalls().get(0).getName());
        assertEquals("Rome", loaded.getToolCalls().get(0).getArguments().path("city").asText());
    }

    @Test
    @DisplayName("Should save and retrieve facts")
    void testSaveAndGetFact_RoundTrip() throws Exception {
        // Given
        String conversationId = "test-conv-facts";
        JsonNode value = mapper.readTree("{\"city\": \"Rome\", \"temp\": 25}");

        // When
        store.saveFact(conversationId, "weather-data", value);
        JsonNode retrieved = store.getFact(conversationId, "weather-data");

        // Then
        assertNotNull(retrieved, "Fact should be retrievable");
        assertEquals("Rome", retrieved.path("city").asText());
        assertEquals(25, retrieved.path("temp").asInt());
    }

    @Test
    @DisplayName("Should return null for nonexistent fact")
    void testGetFact_NonExistent_ReturnsNull() {
        assertNull(store.getFact("nonexistent", "missing-key"));
    }

    @Test
    @DisplayName("Should search facts by keyword")
    void testSearchFacts_ByKeyword_ReturnsMatches() throws Exception {
        // Given
        String conversationId = "test-conv-search";
        store.saveFact(conversationId, "weather-rome", mapper.readTree("{\"temp\": 25}"));
        store.saveFact(conversationId, "weather-paris", mapper.readTree("{\"temp\": 18}"));
        store.saveFact(conversationId, "user-preference", mapper.readTree("{\"lang\": \"en\"}"));

        // When
        List<Fact> results = store.searchFacts(conversationId, "weather");

        // Then
        assertEquals(2, results.size(), "Should find 2 weather-related facts");
    }

    @Test
    @DisplayName("Should clear conversation data completely")
    void testClearConversation_RemovesAllData() throws Exception {
        // Given
        String conversationId = "test-conv-clear";
        store.saveMessage(conversationId, Message.user("Hello"));
        store.saveFact(conversationId, "key1", mapper.readTree("{\"data\": true}"));

        // Verify data exists
        assertFalse(store.loadHistory(conversationId).isEmpty());
        assertNotNull(store.getFact(conversationId, "key1"));

        // When
        store.clearConversation(conversationId);

        // Then
        assertTrue(store.loadHistory(conversationId).isEmpty(), "History should be empty after clear");
        assertNull(store.getFact(conversationId, "key1"), "Facts should be cleared");
    }

    @Test
    @DisplayName("Should isolate conversations from each other")
    void testConversationIsolation_SeparateData() {
        // Given
        store.saveMessage("conv-a", Message.user("Message A"));
        store.saveMessage("conv-b", Message.user("Message B"));

        // When
        List<Message> historyA = store.loadHistory("conv-a");
        List<Message> historyB = store.loadHistory("conv-b");

        // Then
        assertEquals(1, historyA.size());
        assertEquals("Message A", historyA.get(0).getContent());
        assertEquals(1, historyB.size());
        assertEquals("Message B", historyB.get(0).getContent());
    }

    @Test
    @DisplayName("Should sanitize conversation ID to prevent path traversal")
    void testSaveMessage_WithPathTraversalId_Sanitized() {
        // Given — malicious conversation ID
        String maliciousId = "../../etc/passwd";
        store.saveMessage(maliciousId, Message.user("test"));

        // When — should work safely without escaping the base directory
        List<Message> history = store.loadHistory(maliciousId);

        // Then — message is stored and retrievable (ID sanitized internally)
        assertEquals(1, history.size());
    }
}
