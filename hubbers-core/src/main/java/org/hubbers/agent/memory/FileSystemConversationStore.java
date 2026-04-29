package org.hubbers.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.model.FunctionCall;
import org.hubbers.model.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * File-system-backed implementation of {@link ConversationMemory}.
 * Persists conversation history and facts as JSON files under a configurable base directory.
 *
 * <p>Directory structure:
 * <pre>
 *   {baseDir}/{conversationId}/messages.json
 *   {baseDir}/{conversationId}/facts.json
 * </pre>
 *
 * <p>Thread-safe via per-conversation read-write locks.
 */
@Slf4j
public class FileSystemConversationStore implements ConversationMemory {

    private static final String MESSAGES_FILE = "messages.json";
    private static final String FACTS_FILE = "facts.json";

    private final Path baseDir;
    private final ObjectMapper mapper;
    private final Map<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * Creates a new file-system conversation store.
     *
     * @param baseDir the root directory for conversation storage
     * @param mapper  the Jackson ObjectMapper for JSON serialization
     */
    public FileSystemConversationStore(Path baseDir, ObjectMapper mapper) {
        this.baseDir = baseDir;
        this.mapper = mapper;
        log.info("FileSystemConversationStore initialized at: {}", baseDir);
    }

    @Override
    public void saveMessage(String conversationId, Message message) {
        var lock = lockFor(conversationId).writeLock();
        lock.lock();
        try {
            Path messagesPath = conversationDir(conversationId).resolve(MESSAGES_FILE);
            List<ObjectNode> messages = readMessages(messagesPath);
            messages.add(serializeMessage(message));
            writeJson(messagesPath, messages);
            log.debug("Saved message to conversation '{}' (total: {})", conversationId, messages.size());
        } catch (IOException e) {
            log.error("Failed to save message for conversation '{}'", conversationId, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Message> loadHistory(String conversationId) {
        var lock = lockFor(conversationId).readLock();
        lock.lock();
        try {
            Path messagesPath = conversationDir(conversationId).resolve(MESSAGES_FILE);
            if (!Files.exists(messagesPath)) {
                return List.of();
            }
            List<ObjectNode> rawMessages = readMessages(messagesPath);
            return rawMessages.stream()
                    .map(this::deserializeMessage)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to load history for conversation '{}'", conversationId, e);
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveFact(String conversationId, String key, JsonNode value) {
        var lock = lockFor(conversationId).writeLock();
        lock.lock();
        try {
            Path factsPath = conversationDir(conversationId).resolve(FACTS_FILE);
            Map<String, ObjectNode> facts = readFacts(factsPath);
            ObjectNode factNode = mapper.createObjectNode();
            factNode.put("key", key);
            factNode.set("value", value);
            factNode.put("timestamp", System.currentTimeMillis());
            facts.put(key, factNode);
            writeJson(factsPath, facts);
            log.debug("Saved fact '{}' for conversation '{}'", key, conversationId);
        } catch (IOException e) {
            log.error("Failed to save fact '{}' for conversation '{}'", key, conversationId, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public JsonNode getFact(String conversationId, String key) {
        var lock = lockFor(conversationId).readLock();
        lock.lock();
        try {
            Path factsPath = conversationDir(conversationId).resolve(FACTS_FILE);
            if (!Files.exists(factsPath)) {
                return null;
            }
            Map<String, ObjectNode> facts = readFacts(factsPath);
            ObjectNode factNode = facts.get(key);
            return factNode != null ? factNode.get("value") : null;
        } catch (IOException e) {
            log.error("Failed to get fact '{}' for conversation '{}'", key, conversationId, e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Fact> searchFacts(String conversationId, String query) {
        var lock = lockFor(conversationId).readLock();
        lock.lock();
        try {
            Path factsPath = conversationDir(conversationId).resolve(FACTS_FILE);
            if (!Files.exists(factsPath)) {
                return List.of();
            }
            Map<String, ObjectNode> facts = readFacts(factsPath);
            String lowerQuery = query.toLowerCase();
            return facts.values().stream()
                    .filter(node -> node.get("key").asText().toLowerCase().contains(lowerQuery)
                            || node.get("value").toString().toLowerCase().contains(lowerQuery))
                    .map(node -> new Fact(
                            node.get("key").asText(),
                            node.get("value"),
                            node.get("timestamp").asLong()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to search facts for conversation '{}'", conversationId, e);
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        var lock = lockFor(conversationId).writeLock();
        lock.lock();
        try {
            Path dir = conversationDir(conversationId);
            if (Files.exists(dir)) {
                Files.deleteIfExists(dir.resolve(MESSAGES_FILE));
                Files.deleteIfExists(dir.resolve(FACTS_FILE));
                Files.deleteIfExists(dir);
                log.info("Cleared conversation '{}'", conversationId);
            }
        } catch (IOException e) {
            log.error("Failed to clear conversation '{}'", conversationId, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> listConversations() {
        if (!Files.exists(baseDir)) {
            return List.of();
        }
        try (var dirs = Files.list(baseDir)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list conversations", e);
            return List.of();
        }
    }

    // --- Internal helpers ---

    private ReadWriteLock lockFor(String conversationId) {
        return locks.computeIfAbsent(conversationId, k -> new ReentrantReadWriteLock());
    }

    private Path conversationDir(String conversationId) {
        // Sanitize conversationId to prevent directory traversal
        String sanitized = conversationId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return baseDir.resolve(sanitized);
    }

    private void ensureDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private List<ObjectNode> readMessages(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        return mapper.readValue(path.toFile(), new TypeReference<List<ObjectNode>>() {});
    }

    private Map<String, ObjectNode> readFacts(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new ConcurrentHashMap<>();
        }
        return mapper.readValue(path.toFile(), new TypeReference<Map<String, ObjectNode>>() {});
    }

    private void writeJson(Path path, Object data) throws IOException {
        ensureDirectory(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
    }

    private ObjectNode serializeMessage(Message message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", message.getRole());
        node.put("content", message.getContent());
        if (message.getToolCallId() != null) {
            node.put("toolCallId", message.getToolCallId());
        }
        if (message.getName() != null) {
            node.put("name", message.getName());
        }
        if (message.getToolCalls() != null) {
            ArrayNode toolCallsArray = node.putArray("toolCalls");
            for (FunctionCall fc : message.getToolCalls()) {
                ObjectNode fcNode = toolCallsArray.addObject();
                fcNode.put("id", fc.getId());
                fcNode.put("name", fc.getName());
                fcNode.set("arguments", fc.getArguments());
            }
        }
        return node;
    }

    private Message deserializeMessage(ObjectNode node) {
        String role = node.path("role").asText();
        String content = node.path("content").asText(null);
        String toolCallId = node.has("toolCallId") ? node.get("toolCallId").asText() : null;
        String name = node.has("name") ? node.get("name").asText() : null;

        List<FunctionCall> toolCalls = null;
        if (node.has("toolCalls") && node.get("toolCalls").isArray()) {
            toolCalls = new ArrayList<>();
            for (JsonNode fcNode : node.get("toolCalls")) {
                toolCalls.add(new FunctionCall(
                        fcNode.path("id").asText(),
                        fcNode.path("name").asText(),
                        fcNode.get("arguments")));
            }
        }

        return switch (role) {
            case "system" -> Message.system(content);
            case "user" -> Message.user(content);
            case "tool" -> Message.tool(toolCallId, name, content);
            case "assistant" -> toolCalls != null
                    ? Message.assistantWithToolCalls(content, toolCalls)
                    : Message.assistant(content);
            default -> Message.user(content);
        };
    }
}
