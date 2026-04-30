package org.hubbers.tool.builtin;

import org.hubbers.tool.ToolDriver;
import org.hubbers.tool.ToolExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool driver for executing HTTP API calls.
 * 
 * <p>Supports configurable HTTP methods (GET, POST, PUT, DELETE) and automatically
 * serializes input as JSON. Uses HttpRequestBuilder for clean HTTP operations.</p>
 * 
 * <p>Configuration in tool.yaml:
 * <pre>{@code
 * type: http
 * config:
 *   base_url: https://api.example.com/endpoint
 *   method: POST  # Optional, defaults to POST
 * }</pre>
 * 
 * <p>Example usage:
 * <pre>{@code
 * {
 *   "name": "weather.api",
 *   "type": "http",
 *   "config": {
 *     "base_url": "https://api.weather.com/forecast",
 *     "method": "GET"
 *   }
 * }
 * }</pre>
 * 
 * @since 0.1.0
 */
@RequiredArgsConstructor
public class HttpToolDriver implements ToolDriver {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}") ;
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "http";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String toolName = (manifest.getTool() != null && manifest.getTool().getName() != null) 
            ? manifest.getTool().getName() : "http";
        String baseUrl = asString(manifest, "base_url", toolName);
        String method = manifest.getConfig().getOrDefault("method", "POST").toString().toUpperCase();
        Map<String, String> collectionVariables = stringMap(manifest, "variables");
        Map<String, String> pathParameters = resolveNamedValues(stringMap(manifest, "path_params"), input, collectionVariables);
        Map<String, String> queryParameters = resolveNamedValues(stringMap(manifest, "query_params"), input, collectionVariables);
        Map<String, String> headers = resolveNamedValues(stringMap(manifest, "headers"), input, collectionVariables);
        Map<String, String> resolutionContext = buildResolutionContext(input, collectionVariables, pathParameters, queryParameters);
        String resolvedUrl = buildUrl(baseUrl, resolutionContext, pathParameters, queryParameters);
        
        try {
            HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, mapper);
            
            // Build request based on HTTP method
            switch (method) {
                case "GET" -> builder.get(resolvedUrl);
                case "POST" -> builder.post(resolvedUrl);
                case "PUT" -> builder.put(resolvedUrl);
                case "DELETE" -> builder.delete(resolvedUrl);
                default -> throw new ToolExecutionException(
                    toolName,
                    "Unsupported HTTP method: " + method,
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
                );
            }

            headers.forEach(builder::header);

            String bodyTemplate = textConfig(manifest, "body_template");
            String bodyType = manifest.getConfig().getOrDefault("body_type", "json").toString();
            if (bodyTemplate != null && BODY_METHODS.contains(method)) {
                String resolvedBody = resolveTemplate(bodyTemplate, resolutionContext);
                builder.rawBody(resolvedBody, contentType(bodyType));
            } else if (BODY_METHODS.contains(method) && usesDefaultJsonBody(manifest, input)) {
                builder.body(input);
            }
            
            // Execute and return response
            return builder.executeForJson();
            
        } catch (IOException e) {
            throw new ToolExecutionException(
                toolName,
                "HTTP request failed: " + e.getMessage(),
                ToolExecutionException.ErrorCategory.NETWORK_ERROR,
                e
            );
        }
    }

    private String asString(ToolManifest manifest, String key, String toolName) {
        Object value = manifest.getConfig().get(key);
        if (value == null) {
            throw new ToolExecutionException(
                toolName,
                "Missing required config key: " + key,
                ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
            );
        }
        return value.toString();
    }

    private Map<String, String> stringMap(ToolManifest manifest, String key) {
        Object value = manifest.getConfig().get(key);
        if (!(value instanceof Map<?, ?> mapValue)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        mapValue.forEach((mapKey, mapEntryValue) -> {
            if (mapKey != null && mapEntryValue != null) {
                result.put(mapKey.toString(), mapEntryValue.toString());
            }
        });
        return result;
    }

    private Map<String, String> resolveNamedValues(
            Map<String, String> configuredValues,
            JsonNode input,
            Map<String, String> collectionVariables
    ) {
        Map<String, String> resolved = new LinkedHashMap<>();
        Map<String, String> baseContext = buildResolutionContext(input, collectionVariables, Map.of(), Map.of());
        configuredValues.forEach((name, configuredValue) -> {
            if (input.has(name) && !input.get(name).isNull()) {
                resolved.put(name, scalarText(input.get(name)));
            } else {
                resolved.put(name, resolveTemplate(configuredValue, baseContext));
            }
        });
        return resolved;
    }

    private Map<String, String> buildResolutionContext(
            JsonNode input,
            Map<String, String> collectionVariables,
            Map<String, String> pathParameters,
            Map<String, String> queryParameters
    ) {
        Map<String, String> context = new LinkedHashMap<>(collectionVariables);
        input.fields().forEachRemaining(entry -> {
            if (entry.getValue().isValueNode()) {
                context.put(entry.getKey(), scalarText(entry.getValue()));
            }
        });
        context.putAll(pathParameters);
        context.putAll(queryParameters);
        return context;
    }

    private String buildUrl(
            String configuredUrl,
            Map<String, String> resolutionContext,
            Map<String, String> pathParameters,
            Map<String, String> queryParameters
    ) {
        String resolvedUrl = resolveTemplate(configuredUrl, resolutionContext);

        for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
            resolvedUrl = resolvedUrl.replace(":" + entry.getKey(), urlEncode(entry.getValue()));
        }

        if (queryParameters.isEmpty()) {
            return resolvedUrl;
        }

        String separator = resolvedUrl.contains("?") ? "&" : "?";
        String queryString = queryParameters.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return resolvedUrl + separator + queryString;
    }

    private String resolveTemplate(String template, Map<String, String> resolutionContext) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = resolutionContext.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String scalarText(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        return value.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String textConfig(ToolManifest manifest, String key) {
        Object value = manifest.getConfig().get(key);
        return value != null ? value.toString() : null;
    }

    private boolean usesDefaultJsonBody(ToolManifest manifest, JsonNode input) {
        return !manifest.getConfig().containsKey("body_template") && input != null && !input.isEmpty();
    }

    private String contentType(String bodyType) {
        return switch (bodyType) {
            case "text" -> "text/plain";
            case "xml" -> "application/xml";
            case "form-urlencoded" -> "application/x-www-form-urlencoded";
            case "multipart-form" -> "multipart/form-data";
            default -> "application/json";
        };
    }
}
