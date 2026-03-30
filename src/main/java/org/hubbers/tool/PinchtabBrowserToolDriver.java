package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class PinchtabBrowserToolDriver implements ToolDriver {
    private static final String DEFAULT_PINCHTAB_URL = "http://localhost:9867";
    private static final String DEFAULT_PROFILE = "default";
    
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Map<String, SessionState> sessions = new HashMap<>();

    public PinchtabBrowserToolDriver(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "browser.pinchtab";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String action = input.path("action").asText(null);
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Missing required field: action");
        }

        return switch (action) {
            case "navigate" -> executeNavigate(manifest, input);
            case "snapshot" -> executeSnapshot(manifest, input);
            case "click" -> executeClick(manifest, input);
            case "fill" -> executeFill(manifest, input);
            case "press" -> executePress(manifest, input);
            case "extract_text" -> executeExtractText(manifest, input);
            case "screenshot" -> executeScreenshot(manifest, input);
            case "close" -> executeClose(manifest, input);
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    private JsonNode executeNavigate(ToolManifest manifest, JsonNode input) {
        String url = input.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing required field: url");
        }

        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            // Navigate to URL using /navigate endpoint
            ObjectNode navPayload = mapper.createObjectNode();
            navPayload.put("url", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/navigate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(navPayload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab navigate failed: " + response.statusCode() + " - " + response.body());
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "navigate");
            output.put("profile", profile);
            output.put("url", url);
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab navigate failed for URL: " + url, e);
        }
    }

    private JsonNode executeSnapshot(ToolManifest manifest, JsonNode input) {
        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String filter = input.path("filter").asText("interactive");
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/snapshot?filter=" + filter))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab snapshot failed: " + response.statusCode() + " - " + response.body());
            }

            JsonNode snapshotData = mapper.readTree(response.body());
            
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "snapshot");
            output.put("profile", profile);
            output.set("snapshot", snapshotData);
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab snapshot failed", e);
        }
    }

    private JsonNode executeClick(ToolManifest manifest, JsonNode input) {
        String ref = input.path("ref").asText(null);
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Missing required field: ref");
        }

        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            ObjectNode actionPayload = mapper.createObjectNode();
            actionPayload.put("kind", "click");
            actionPayload.put("ref", ref);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/action"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(actionPayload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab click failed: " + response.statusCode() + " - " + response.body());
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "click");
            output.put("profile", profile);
            output.put("ref", ref);
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab click failed for ref: " + ref, e);
        }
    }

    private JsonNode executeFill(ToolManifest manifest, JsonNode input) {
        String ref = input.path("ref").asText(null);
        String text = input.path("text").asText(null);
        
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Missing required field: ref");
        }
        if (text == null) {
            throw new IllegalArgumentException("Missing required field: text");
        }

        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            ObjectNode actionPayload = mapper.createObjectNode();
            actionPayload.put("kind", "type");
            actionPayload.put("ref", ref);
            actionPayload.put("value", text);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/action"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(actionPayload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab fill failed: " + response.statusCode() + " - " + response.body());
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "fill");
            output.put("profile", profile);
            output.put("ref", ref);
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab fill failed", e);
        }
    }

    private JsonNode executePress(ToolManifest manifest, JsonNode input) {
        String ref = input.path("ref").asText(null);
        String key = input.path("key").asText(null);
        
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Missing required field: ref");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Missing required field: key");
        }

        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            ObjectNode actionPayload = mapper.createObjectNode();
            actionPayload.put("kind", "press");
            actionPayload.put("ref", ref);
            actionPayload.put("key", key);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/action"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(actionPayload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab press failed: " + response.statusCode() + " - " + response.body());
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "press");
            output.put("profile", profile);
            output.put("ref", ref);
            output.put("key", key);
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab press failed", e);
        }
    }

    private JsonNode executeExtractText(ToolManifest manifest, JsonNode input) {
        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/text"))
                    .header("Accept", "text/plain")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab extract_text failed: " + response.statusCode() + " - " + response.body());
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "extract_text");
            output.put("profile", profile);
            output.put("text", response.body());
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab extract_text failed", e);
        }
    }

    private JsonNode executeScreenshot(ToolManifest manifest, JsonNode input) {
        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        String mode = input.path("mode").asText(null);
        String pinchtabUrl = resolvePinchtabUrl(manifest);
        
        try {
            SessionState session = getOrCreateSession(manifest, profile, mode, pinchtabUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/tabs/" + session.tabId + "/screenshot"))
                    .header("Accept", "image/png")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Pinchtab screenshot failed: " + response.statusCode() + " - " + response.body());
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "screenshot");
            output.put("profile", profile);
            output.put("screenshot", response.body());
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Pinchtab screenshot failed", e);
        }
    }

    private JsonNode executeClose(ToolManifest manifest, JsonNode input) {
        String profile = input.path("profile").asText(DEFAULT_PROFILE);
        
        SessionState session = sessions.remove(profile);
        if (session == null) {
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("action", "close");
            output.put("profile", profile);
            output.put("message", "No active session for profile");
            return output;
        }

        // Note: We could optionally close the instance/tab via API here
        // For now, we just remove from our session map
        ObjectNode output = mapper.createObjectNode();
        output.put("success", true);
        output.put("action", "close");
        output.put("profile", profile);
        return output;
    }

    private SessionState getOrCreateSession(ToolManifest manifest, String profileName, String requestedMode, String pinchtabUrl) throws IOException, InterruptedException {
        SessionState existing = sessions.get(profileName);
        if (existing != null) {
            return existing;
        }

        // Get profile config
        Map<String, Object> profiles = getProfilesConfig(manifest);
        @SuppressWarnings("unchecked")
        Map<String, Object> profileConfig = (Map<String, Object>) profiles.get(profileName);
        
        // Determine mode: input parameter > profile config > default
        String mode;
        if (requestedMode != null && !requestedMode.isBlank()) {
            mode = requestedMode;
        } else if (profileConfig != null && profileConfig.containsKey("mode")) {
            mode = (String) profileConfig.get("mode");
        } else {
            mode = "headless";
        }
        
        String profileDisplayName = profileName;
        if (profileConfig != null) {
            profileDisplayName = (String) profileConfig.getOrDefault("name", profileName);
        }

        String profileId = null;
        
        // Try to create profile
        ObjectNode profilePayload = mapper.createObjectNode();
        profilePayload.put("name", profileDisplayName);
        
        HttpRequest createProfileRequest = HttpRequest.newBuilder()
                .uri(URI.create(pinchtabUrl + "/profiles"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(profilePayload)))
                .build();
        
        HttpResponse<String> profileResponse = httpClient.send(createProfileRequest, HttpResponse.BodyHandlers.ofString());
        
        if (profileResponse.statusCode() == 409) {
            // Profile already exists, retrieve it
            HttpRequest listProfilesRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/profiles"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> listResponse = httpClient.send(listProfilesRequest, HttpResponse.BodyHandlers.ofString());
            
            if (listResponse.statusCode() >= 400) {
                throw new IllegalStateException("Failed to list Pinchtab profiles: " + listResponse.statusCode() + " - " + listResponse.body());
            }
            
            JsonNode profilesList = mapper.readTree(listResponse.body());
            if (profilesList.isArray()) {
                for (JsonNode profile : profilesList) {
                    if (profileDisplayName.equals(profile.path("name").asText())) {
                        profileId = profile.path("id").asText();
                        break;
                    }
                }
            }
            
            if (profileId == null) {
                throw new IllegalStateException("Profile '" + profileDisplayName + "' exists but could not be found in profiles list");
            }
        } else if (profileResponse.statusCode() >= 400) {
            throw new IllegalStateException("Failed to create Pinchtab profile: " + profileResponse.statusCode() + " - " + profileResponse.body());
        } else {
            JsonNode profileResult = mapper.readTree(profileResponse.body());
            profileId = profileResult.path("id").asText();
        }

        // Start instance
        ObjectNode instancePayload = mapper.createObjectNode();
        instancePayload.put("profileId", profileId);
        instancePayload.put("mode", mode);
        
        HttpRequest startInstanceRequest = HttpRequest.newBuilder()
                .uri(URI.create(pinchtabUrl + "/instances/start"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(instancePayload)))
                .build();
        
        HttpResponse<String> instanceResponse = httpClient.send(startInstanceRequest, HttpResponse.BodyHandlers.ofString());
        
        String instanceId;
        
        if (instanceResponse.statusCode() == 409) {
            // Instance already exists for this profile, retrieve it
            HttpRequest listInstancesRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/instances"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> listResponse = httpClient.send(listInstancesRequest, HttpResponse.BodyHandlers.ofString());
            
            if (listResponse.statusCode() >= 400) {
                throw new IllegalStateException("Failed to list Pinchtab instances: " + listResponse.statusCode() + " - " + listResponse.body());
            }
            
            JsonNode instancesList = mapper.readTree(listResponse.body());
            instanceId = null;
            
            if (instancesList.isArray()) {
                for (JsonNode instance : instancesList) {
                    if (profileId.equals(instance.path("profileId").asText())) {
                        instanceId = instance.path("id").asText();
                        break;
                    }
                }
            }
            
            if (instanceId == null) {
                throw new IllegalStateException("Instance for profile '" + profileId + "' exists but could not be found in instances list");
            }
        } else if (instanceResponse.statusCode() >= 400) {
            throw new IllegalStateException("Failed to start Pinchtab instance: " + instanceResponse.statusCode() + " - " + instanceResponse.body());
        } else {
            JsonNode instanceResult = mapper.readTree(instanceResponse.body());
            instanceId = instanceResult.path("id").asText();
        }

        // Wait for instance to be ready (with retry)
        waitForInstanceReady(pinchtabUrl, instanceId);

        // Open tab
        ObjectNode tabPayload = mapper.createObjectNode();
        tabPayload.put("url", "about:blank");
        
        HttpRequest openTabRequest = HttpRequest.newBuilder()
                .uri(URI.create(pinchtabUrl + "/instances/" + instanceId + "/tabs/open"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(tabPayload)))
                .build();
        
        HttpResponse<String> tabResponse = httpClient.send(openTabRequest, HttpResponse.BodyHandlers.ofString());
        
        if (tabResponse.statusCode() >= 400) {
            throw new IllegalStateException("Failed to open Pinchtab tab: " + tabResponse.statusCode() + " - " + tabResponse.body());
        }
        
        JsonNode tabResult = mapper.readTree(tabResponse.body());
        String tabId = tabResult.path("tabId").asText();

        SessionState session = new SessionState(profileId, instanceId, tabId);
        sessions.put(profileName, session);
        return session;
    }

    private void waitForInstanceReady(String pinchtabUrl, String instanceId) throws IOException, InterruptedException {
        int maxRetries = 20;  // 20 retries = 10 seconds max
        int retryDelayMs = 500;  // 500ms between retries
        
        for (int i = 0; i < maxRetries; i++) {
            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pinchtabUrl + "/instances/" + instanceId))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            
            if (statusResponse.statusCode() >= 400) {
                throw new IllegalStateException("Failed to check Pinchtab instance status: " + statusResponse.statusCode() + " - " + statusResponse.body());
            }
            
            JsonNode statusResult = mapper.readTree(statusResponse.body());
            String status = statusResult.path("status").asText();
            
            if ("running".equals(status)) {
                return;  // Instance is ready
            }
            
            if ("failed".equals(status) || "stopped".equals(status)) {
                throw new IllegalStateException("Pinchtab instance failed to start: status=" + status);
            }
            
            // Still starting, wait and retry
            Thread.sleep(retryDelayMs);
        }
        
        throw new IllegalStateException("Pinchtab instance did not become ready within timeout (10 seconds)");
    }

    private String resolvePinchtabUrl(ToolManifest manifest) {
        Object configured = manifest.getConfig() == null ? null : manifest.getConfig().get("pinchtab_url");
        if (configured != null && !configured.toString().isBlank()) {
            return configured.toString();
        }
        return DEFAULT_PINCHTAB_URL;
    }

    private Map<String, Object> getProfilesConfig(ToolManifest manifest) {
        if (manifest.getConfig() == null) {
            return createDefaultProfiles();
        }
        
        Object profilesObj = manifest.getConfig().get("profiles");
        if (profilesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> profiles = (Map<String, Object>) profilesObj;
            return profiles;
        }
        
        return createDefaultProfiles();
    }

    private Map<String, Object> createDefaultProfiles() {
        Map<String, Object> profiles = new HashMap<>();
        Map<String, Object> defaultProfile = new HashMap<>();
        defaultProfile.put("name", "default");
        defaultProfile.put("mode", "headless");
        profiles.put("default", defaultProfile);
        return profiles;
    }

    private static class SessionState {
        final String profileId;
        final String instanceId;
        final String tabId;

        SessionState(String profileId, String instanceId, String tabId) {
            this.profileId = profileId;
            this.instanceId = instanceId;
            this.tabId = tabId;
        }
    }
}
