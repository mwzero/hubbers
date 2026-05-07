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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool driver that executes an operation from an OpenAPI specification.
 *
 * <p>Reads an OpenAPI 3.x spec (YAML or JSON), locates the operation matching the
 * configured {@code operation_id}, resolves path/query/header parameters from the
 * runtime input, and executes the HTTP call.
 *
 * <p>Configuration in {@code tool.yaml}:
 * <pre>{@code
 * type: openapi
 * config:
 *   spec: openapi/petstore.yaml   # path relative to repo root
 *   operation_id: listPets        # operationId from the spec
 *   base_url: https://api.example.com   # optional override of servers[0].url
 * }</pre>
 *
 * <p>Input fields are mapped to parameters by name. Request body is sent as JSON.
 *
 * @since 0.2.0
 */
@Slf4j
@RequiredArgsConstructor
public class OpenApiToolDriver implements ToolDriver {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final Path repoRoot;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    @Override
    public String type() {
        return "openapi";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String specPath   = requireConfig(manifest, "spec");
        String operationId = requireConfig(manifest, "operation_id");
        String baseUrlOverride = optionalConfig(manifest, "base_url");

        Path specFile = repoRoot.resolve(specPath);
        if (!Files.exists(specFile)) {
            throw new ToolExecutionException(
                    "openapi",
                    "OpenAPI spec not found: " + specFile,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }

        JsonNode spec = parseSpec(specFile);

        // Resolve base URL
        String baseUrl = resolveBaseUrl(spec, baseUrlOverride);

        // Find operation
        OperationInfo op = findOperation(spec, operationId);
        if (op == null) {
            throw new ToolExecutionException(
                    "openapi",
                    "Operation not found in spec: " + operationId,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }

        // Build URL with path parameters resolved
        String url = buildUrl(baseUrl, op.path(), op.parameters(), input);

        log.debug("OpenAPI driver: {} {} (spec={}, operationId={})", op.method(), url, specPath, operationId);

        try {
            HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, jsonMapper);
            String method = op.method().toUpperCase();
            switch (method) {
                case "GET"    -> builder.get(url);
                case "POST"   -> builder.post(url);
                case "PUT"    -> builder.put(url);
                case "DELETE" -> builder.delete(url);
                default -> builder.post(url);
            }

            builder.header("Accept", "application/json");
            builder.header("Content-Type", "application/json");

            if (BODY_METHODS.contains(method) && input != null && !input.isEmpty()) {
                // Filter out path/query parameters from body
                var bodyFields = filterBodyFields(input, op.parameters());
                if (!bodyFields.isEmpty()) {
                    builder.body(jsonMapper.valueToTree(bodyFields));
                }
            }

            return builder.executeForJson();

        } catch (IOException e) {
            throw new ToolExecutionException(
                    "openapi",
                    "HTTP execution failed: " + e.getMessage(),
                    ToolExecutionException.ErrorCategory.NETWORK_ERROR
            );
        }
    }

    // ─── Spec parsing ─────────────────────────────────────────────────────────

    private JsonNode parseSpec(Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                return new YAMLMapper().readTree(file.toFile());
            }
            return jsonMapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new ToolExecutionException(
                    "openapi",
                    "Failed to parse OpenAPI spec: " + e.getMessage(),
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }
    }

    private String resolveBaseUrl(JsonNode spec, String override) {
        if (override != null && !override.isBlank()) {
            return override.replaceAll("/+$", "");
        }
        JsonNode servers = spec.path("servers");
        if (servers.isArray() && servers.size() > 0) {
            String url = servers.get(0).path("url").asText("");
            return url.replaceAll("/+$", "");
        }
        return "";
    }

    /** Walks paths/methods looking for operationId match. Returns null if not found. */
    private OperationInfo findOperation(JsonNode spec, String targetOperationId) {
        JsonNode paths = spec.path("paths");
        if (paths.isMissingNode()) return null;

        var pathIter = paths.fields();
        while (pathIter.hasNext()) {
            var pathEntry = pathIter.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            for (String method : List.of("get", "post", "put", "delete", "patch", "head", "options")) {
                JsonNode operation = pathItem.path(method);
                if (operation.isMissingNode()) continue;

                String opId = operation.path("operationId").asText("");
                if (targetOperationId.equals(opId)) {
                    List<ParameterInfo> params = collectParameters(pathItem, operation);
                    return new OperationInfo(method, path, params);
                }
            }
        }
        return null;
    }

    private List<ParameterInfo> collectParameters(JsonNode pathItem, JsonNode operation) {
        List<ParameterInfo> params = new ArrayList<>();
        // Path-level parameters
        addParameters(params, pathItem.path("parameters"));
        // Operation-level parameters (override path-level by name)
        addParameters(params, operation.path("parameters"));
        return params;
    }

    private void addParameters(List<ParameterInfo> params, JsonNode parametersNode) {
        if (!parametersNode.isArray()) return;
        for (JsonNode p : parametersNode) {
            String name = p.path("name").asText();
            String in   = p.path("in").asText();
            boolean required = p.path("required").asBoolean(false);
            params.removeIf(existing -> existing.name().equals(name));
            params.add(new ParameterInfo(name, in, required));
        }
    }

    // ─── URL building ─────────────────────────────────────────────────────────

    private String buildUrl(String baseUrl, String pathTemplate, List<ParameterInfo> params, JsonNode input) {
        String path = pathTemplate;

        List<String> queryParts = new ArrayList<>();

        for (ParameterInfo param : params) {
            JsonNode value = input != null ? input.path(param.name()) : null;
            if (value == null || value.isMissingNode()) continue;

            String strValue = value.asText();
            switch (param.in()) {
                case "path" -> path = path.replace("{" + param.name() + "}", urlEncode(strValue));
                case "query" -> queryParts.add(urlEncode(param.name()) + "=" + urlEncode(strValue));
                default -> { /* header params not handled here; body handled separately */ }
            }
        }

        String fullUrl = baseUrl + path;
        if (!queryParts.isEmpty()) {
            fullUrl += "?" + String.join("&", queryParts);
        }
        return fullUrl;
    }

    private Map<String, Object> filterBodyFields(JsonNode input, List<ParameterInfo> params) {
        Set<String> paramNames = new java.util.HashSet<>();
        for (ParameterInfo p : params) {
            if ("path".equals(p.in()) || "query".equals(p.in()) || "header".equals(p.in())) {
                paramNames.add(p.name());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        input.fields().forEachRemaining(e -> {
            if (!paramNames.contains(e.getKey())) {
                body.put(e.getKey(), e.getValue());
            }
        });
        return body;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ─── Config helpers ───────────────────────────────────────────────────────

    private String requireConfig(ToolManifest manifest, String key) {
        Object v = manifest.getConfig().get(key);
        if (v == null || v.toString().isBlank()) {
            throw new ToolExecutionException(
                    "openapi",
                    "Missing required config field: " + key,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }
        return v.toString();
    }

    private String optionalConfig(ToolManifest manifest, String key) {
        Object v = manifest.getConfig().get(key);
        return v != null ? v.toString() : null;
    }

    // ─── Value types ──────────────────────────────────────────────────────────

    private record OperationInfo(String method, String path, List<ParameterInfo> parameters) {}
    private record ParameterInfo(String name, String in, boolean required) {}
}
