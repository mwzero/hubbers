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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class CsvWriteToolDriver implements ToolDriver {
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "csv.write";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String filePath = resolveFilePath(manifest, input);
        ArrayNode items = input.path("items").isArray() ? (ArrayNode) input.path("items") : mapper.createArrayNode();
        try {
            Path path = resolvePath(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            List<String> headers = collectHeaders(items);
            List<String> lines = new ArrayList<>();
            lines.add(String.join(",", headers));
            for (JsonNode item : items) {
                List<String> row = new ArrayList<>();
                for (String header : headers) {
                    row.add(escapeCsv(stringify(item.get(header))));
                }
                lines.add(String.join(",", row));
            }
            Files.write(path, lines, StandardCharsets.UTF_8);

            ObjectNode output = mapper.createObjectNode();
            output.put("file_path", path.toString());
            output.put("rows", items.size());
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("CSV write failed", e);
        }
    }

    private List<String> collectHeaders(ArrayNode items) {
        Set<String> headers = new LinkedHashSet<>();
        for (JsonNode item : items) {
            if (!item.isObject()) {
                continue;
            }
            item.fieldNames().forEachRemaining(headers::add);
        }
        return new ArrayList<>(headers);
    }

    private String stringify(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            return value.toString();
        }
    }

    private String escapeCsv(String raw) {
        if (raw == null) {
            return "";
        }
        boolean needQuotes = raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r");
        String escaped = raw.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
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
