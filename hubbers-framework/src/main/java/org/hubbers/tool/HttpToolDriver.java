package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.HttpRequestBuilder;

import java.io.IOException;
import java.net.http.HttpClient;

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
        
        try {
            HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, mapper);
            
            // Build request based on HTTP method
            switch (method) {
                case "GET" -> builder.get(baseUrl);
                case "POST" -> builder.post(baseUrl).body(input);
                case "PUT" -> builder.put(baseUrl).body(input);
                case "DELETE" -> builder.delete(baseUrl);
                default -> throw new ToolExecutionException(
                    toolName, 
                    "Unsupported HTTP method: " + method, 
                    ToolExecutionException.ErrorCategory.CONFIGURATION_ERROR
                );
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
}
