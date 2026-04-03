package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class FileOpsToolDriver implements ToolDriver {
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "file.ops";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String operation = input.path("operation").asText();
        String pathStr = input.path("path").asText();
        
        if (operation.isEmpty() || pathStr.isEmpty()) {
            throw new IllegalArgumentException("Missing required fields: operation and path");
        }

        // Security: reject paths with directory traversal
        if (pathStr.contains("..")) {
            throw new SecurityException("Path traversal not allowed: " + pathStr);
        }

        Path path = resolvePath(pathStr);

        return switch (operation) {
            case "read" -> read(path);
            case "write" -> write(path, input.path("content").asText());
            case "append" -> append(path, input.path("content").asText());
            case "copy" -> copy(path, input.path("destination").asText());
            case "move" -> move(path, input.path("destination").asText());
            case "delete" -> delete(path);
            case "list" -> list(path, input.path("pattern").asText(""), input.path("recursive").asBoolean(false));
            case "mkdir" -> mkdir(path);
            case "exists" -> exists(path);
            case "info" -> info(path);
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    private JsonNode read(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            if (Files.isDirectory(path)) {
                throw new IllegalArgumentException("Path is a directory: " + path);
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("content", content);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + path, e);
        }
    }

    private JsonNode write(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("message", "File written successfully");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write file: " + path, e);
        }
    }

    private JsonNode append(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            String existing = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
            Files.writeString(path, existing + content, StandardCharsets.UTF_8);
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("message", "Content appended successfully");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append to file: " + path, e);
        }
    }

    private JsonNode copy(Path source, String destinationStr) {
        try {
            if (!Files.exists(source)) {
                throw new IllegalArgumentException("Source file not found: " + source);
            }
            if (destinationStr.contains("..")) {
                throw new SecurityException("Path traversal not allowed in destination: " + destinationStr);
            }
            Path destination = resolvePath(destinationStr);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("message", "File copied successfully");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy file: " + source, e);
        }
    }

    private JsonNode move(Path source, String destinationStr) {
        try {
            if (!Files.exists(source)) {
                throw new IllegalArgumentException("Source file not found: " + source);
            }
            if (destinationStr.contains("..")) {
                throw new SecurityException("Path traversal not allowed in destination: " + destinationStr);
            }
            Path destination = resolvePath(destinationStr);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("message", "File moved successfully");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to move file: " + source, e);
        }
    }

    private JsonNode delete(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Path not found: " + path);
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to delete: " + p, e);
                        }
                    });
                }
            } else {
                Files.delete(path);
            }
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("message", "Deleted successfully");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete: " + path, e);
        }
    }

    private JsonNode list(Path path, String pattern, boolean recursive) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Directory not found: " + path);
            }
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("Path is not a directory: " + path);
            }
            ArrayNode items = mapper.createArrayNode();
            Stream<Path> stream = recursive ? Files.walk(path) : Files.list(path);
            stream.filter(p -> !p.equals(path))
                  .filter(p -> pattern.isEmpty() || p.getFileName().toString().matches(pattern))
                  .forEach(p -> items.add(path.relativize(p).toString()));
            stream.close();
            
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.set("items", items);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list directory: " + path, e);
        }
    }

    private JsonNode mkdir(Path path) {
        try {
            Files.createDirectories(path);
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("message", "Directory created successfully");
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + path, e);
        }
    }

    private JsonNode exists(Path path) {
        ObjectNode output = mapper.createObjectNode();
        output.put("success", true);
        output.put("message", Files.exists(path) ? "Path exists" : "Path does not exist");
        
        ObjectNode info = mapper.createObjectNode();
        info.put("exists", Files.exists(path));
        output.set("info", info);
        return output;
    }

    private JsonNode info(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Path not found: " + path);
            }
            ObjectNode info = mapper.createObjectNode();
            info.put("size", Files.size(path));
            info.put("lastModified", DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()).atOffset(ZoneOffset.UTC)));
            info.put("isDirectory", Files.isDirectory(path));
            info.put("isFile", Files.isRegularFile(path));
            
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.set("info", info);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get file info: " + path, e);
        }
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(".").resolve(path).normalize();
    }
}
