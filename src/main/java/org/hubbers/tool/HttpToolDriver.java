package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
        String baseUrl = asString(manifest, "base_url");
        String method = manifest.getConfig().getOrDefault("method", "POST").toString();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json");
            String payload = mapper.writeValueAsString(input);
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else {
                builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(payload));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP tool failed: " + response.statusCode() + " - " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP tool failed", e);
        }
    }

    private String asString(ToolManifest manifest, String key) {
        Object value = manifest.getConfig().get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return value.toString();
    }
}
