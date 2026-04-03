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
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CsvReadToolDriver implements ToolDriver {
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "csv.read";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String filePath = resolveFilePath(manifest, input);
        Path path = resolvePath(filePath);
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("CSV file not found: " + path);
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            ArrayNode items = mapper.createArrayNode();
            if (lines.isEmpty()) {
                ObjectNode out = mapper.createObjectNode();
                out.set("items", items);
                out.put("rows", 0);
                return out;
            }

            List<String> headers = parseCsvLine(lines.get(0));
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(lines.get(i));
                ObjectNode row = mapper.createObjectNode();
                for (int c = 0; c < headers.size(); c++) {
                    String value = c < values.size() ? values.get(c) : "";
                    row.put(headers.get(c), value);
                }
                items.add(row);
            }

            ObjectNode output = mapper.createObjectNode();
            output.set("items", items);
            output.put("rows", items.size());
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("CSV read failed", e);
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private String resolveFilePath(ToolManifest manifest, JsonNode input) {
        JsonNode inputPath = input.get("file_path");
        if (inputPath != null && inputPath.isTextual() && !inputPath.asText().isBlank()) {
            return inputPath.asText();
        }
        Object configured = manifest.getConfig() == null ? null : manifest.getConfig().get("default_file_path");
        if (configured != null && !configured.toString().isBlank()) {
            return configured.toString();
        }
        throw new IllegalArgumentException("Missing file_path");
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(".").resolve(path).normalize();
    }
}
