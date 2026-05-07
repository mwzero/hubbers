package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolExecutionException;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool driver that executes a specific request from a Bruno API collection.
 *
 * <p>Bruno (usebruno.com) is a Git-native API client. This driver reads a Bruno
 * request file ({@code .yml}), resolves collection-level variables, merges runtime
 * input from the tool manifest and the JSON payload, and executes the resulting
 * HTTP call.
 *
 * <p>Configuration in {@code tool.yaml}:
 * <pre>{@code
 * type: bruno
 * config:
 *   collection: "Hubbers API"          # folder name under repo/bruno/
 *   request: "Conversations/List Conversations"  # path inside the collection (without .yml)
 *   base_url: http://localhost:7070    # optional override; uses collection variable if absent
 *   environment: Local                 # optional environment name to load
 * }</pre>
 *
 * <p>Variables are resolved in priority order:
 * <ol>
 *   <li>Runtime input fields (from JSON payload)
 *   <li>Collection-level variables from {@code opencollection.yml}
 * </ol>
 *
 * @since 0.2.0
 */
@Slf4j
@RequiredArgsConstructor
public class BrunoToolDriver implements ToolDriver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    /** Base path of the repo (resolved at construction time). */
    private final Path repoRoot;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    @Override
    public String type() {
        return "bruno";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String collection = requireConfig(manifest, "collection");
        String request    = requireConfig(manifest, "request");

        Path collectionDir = repoRoot.resolve("bruno").resolve(collection);
        if (!Files.isDirectory(collectionDir)) {
            throw new ToolExecutionException(
                    "bruno",
                    "Bruno collection not found: " + collectionDir,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }

        // Load collection-level variables from opencollection.yml
        Map<String, String> collectionVars = loadCollectionVars(collectionDir);

        // Config-level base_url overrides collection variable
        String configBaseUrl = optionalConfig(manifest, "base_url");
        if (configBaseUrl != null && !configBaseUrl.isBlank()) {
            collectionVars.put("baseUrl", configBaseUrl);
        }

        // Load the request YAML
        Path requestFile = collectionDir.resolve(request + ".yml");
        if (!Files.exists(requestFile)) {
            // Try with leading path separator normalisation
            requestFile = collectionDir.resolve(request.replace("/", java.io.File.separator) + ".yml");
        }
        if (!Files.exists(requestFile)) {
            throw new ToolExecutionException(
                    "bruno",
                    "Bruno request file not found: " + requestFile,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }

        JsonNode requestNode = parseYaml(requestFile);

        JsonNode httpNode = requestNode.path("http");
        if (httpNode.isMissingNode()) {
            throw new ToolExecutionException(
                    "bruno",
                    "Request file has no 'http' section: " + requestFile,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }

        String method = httpNode.path("method").asText("GET").toUpperCase();
        String rawUrl  = httpNode.path("url").asText();

        // Build variable resolution context: input fields override collection vars
        Map<String, String> vars = new HashMap<>(collectionVars);
        if (input != null && input.isObject()) {
            input.fields().forEachRemaining(e -> vars.put(e.getKey(), e.getValue().asText()));
        }

        String resolvedUrl = resolveVars(rawUrl, vars);

        // Build headers from request (auth handled via inherited/collection bearer token)
        Map<String, String> headers = resolveHeaders(requestNode, vars, collectionVars);

        // Body: from request file body section or from runtime input
        String bodyText = resolveBody(requestNode, vars);

        log.debug("Bruno driver: {} {} (collection={}, request={})", method, resolvedUrl, collection, request);

        try {
            HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, jsonMapper);
            switch (method) {
                case "GET"    -> builder.get(resolvedUrl);
                case "POST"   -> builder.post(resolvedUrl);
                case "PUT"    -> builder.put(resolvedUrl);
                case "DELETE" -> builder.delete(resolvedUrl);
                case "PATCH"  -> builder.post(resolvedUrl); // PATCH falls back to POST
                default -> throw new ToolExecutionException(
                        "bruno",
                        "Unsupported HTTP method: " + method,
                        ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
                );
            }

            headers.forEach(builder::header);

            if (bodyText != null && !bodyText.isBlank() && BODY_METHODS.contains(method)) {
                builder.rawBody(bodyText, "application/json");
            } else if (BODY_METHODS.contains(method) && input != null && !input.isEmpty()) {
                builder.body(input);
            }

            return builder.executeForJson();

        } catch (IOException e) {
            throw new ToolExecutionException(
                    "bruno",
                    "HTTP execution failed: " + e.getMessage(),
                    ToolExecutionException.ErrorCategory.NETWORK_ERROR
            );
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, String> loadCollectionVars(Path collectionDir) {
        Path collectionFile = collectionDir.resolve("opencollection.yml");
        Map<String, String> vars = new LinkedHashMap<>();
        if (!Files.exists(collectionFile)) {
            return vars;
        }
        try {
            YAMLMapper yaml = new YAMLMapper();
            Map<?, ?> root = yaml.readValue(collectionFile.toFile(), Map.class);
            Object requestObj = root.get("request");
            if (requestObj instanceof Map<?, ?> requestMap) {
                Object variablesObj = requestMap.get("variables");
                if (variablesObj instanceof List<?> variablesList) {
                    for (Object item : variablesList) {
                        if (item instanceof Map<?, ?> varMap) {
                            String name  = String.valueOf(varMap.get("name"));
                            Object value = varMap.get("value");
                            if (name != null && value != null) {
                                vars.put(name, String.valueOf(value));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not parse opencollection.yml at {}: {}", collectionFile, e.getMessage());
        }
        return vars;
    }

    private Map<String, String> resolveHeaders(JsonNode requestNode, Map<String, String> vars,
                                                Map<String, String> collectionVars) {
        Map<String, String> headers = new LinkedHashMap<>();

        // Add auth bearer from collection if present
        JsonNode collectionAuth = findCollectionAuth(requestNode);
        if (collectionAuth != null && "bearer".equals(collectionAuth.path("type").asText())) {
            String token = resolveVars(collectionAuth.path("token").asText(""), vars);
            if (!token.isBlank()) {
                headers.put("Authorization", "Bearer " + token);
            }
        }

        // Request-level headers
        JsonNode headersNode = requestNode.path("http").path("headers");
        if (headersNode.isArray()) {
            for (JsonNode h : headersNode) {
                String name  = h.path("name").asText();
                String value = resolveVars(h.path("value").asText(), vars);
                if (!name.isBlank()) {
                    headers.put(name, value);
                }
            }
        }

        headers.putIfAbsent("Content-Type", "application/json");
        return headers;
    }

    private JsonNode findCollectionAuth(JsonNode requestNode) {
        JsonNode auth = requestNode.path("http").path("auth");
        if (!auth.isMissingNode() && !"inherit".equals(auth.asText())) {
            return auth;
        }
        return null;
    }

    private String resolveBody(JsonNode requestNode, Map<String, String> vars) {
        JsonNode body = requestNode.path("body");
        if (body.isMissingNode()) {
            return null;
        }
        String type = body.path("type").asText("json");
        if ("json".equals(type)) {
            JsonNode data = body.path("data");
            if (data.isTextual()) {
                return resolveVars(data.asText(), vars);
            }
            if (data.isObject() || data.isArray()) {
                return data.toString();
            }
        }
        return null;
    }

    private String resolveVars(String template, Map<String, String> vars) {
        if (template == null) return "";
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = vars.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private JsonNode parseYaml(Path file) {
        try {
            YAMLMapper yaml = new YAMLMapper();
            return yaml.readTree(file.toFile());
        } catch (IOException e) {
            throw new ToolExecutionException(
                    "bruno",
                    "Failed to parse YAML file: " + file + " – " + e.getMessage(),
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }
    }

    private String requireConfig(ToolManifest manifest, String key) {
        Object value = manifest.getConfig().get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ToolExecutionException(
                    "bruno",
                    "Missing required config field: " + key,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }
        return value.toString();
    }

    private String optionalConfig(ToolManifest manifest, String key) {
        Object value = manifest.getConfig().get(key);
        return value != null ? value.toString() : null;
    }
}
