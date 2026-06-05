package org.hubbers.react;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hubbers.react.model.Message;
import org.hubbers.react.model.ModelProvider;
import org.hubbers.react.model.ModelRequest;
import org.hubbers.react.model.ModelResponse;

/**
 * Minimal llama.cpp server HTTP provider for standalone demo use.
 *
 * <p>Targets the llama.cpp OpenAI-compatible endpoint:
 * {@code POST http://localhost:8080/v1/chat/completions}.</p>
 *
 * <p>All requests and responses are traced to {@code llamacpp-trace.log}
 * in the working directory.</p>
 *
 * <p>Production code should use {@code OllamaModelProvider} or a dedicated
 * {@code LlamaCppModelProvider} from {@code hubbers-core}.</p>
 */
public class MinimalLlamaCppProvider implements ModelProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final Pattern CONTENT_PATTERN = Pattern.compile(
            "\"content\"\\s*:\\s*\"(.*?)\"\\s*[,}]", Pattern.DOTALL);
    private static final Pattern UNICODE_PATTERN  = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    private static final Path TRACE_FILE = Paths.get("llamacpp-trace.log");

    private final String      baseUrl;
    private final HttpClient  httpClient;

    MinimalLlamaCppProvider() {
        this(DEFAULT_BASE_URL);
    }

    MinimalLlamaCppProvider(String baseUrl) {
        this.baseUrl    = baseUrl.replaceAll("/+$", ""); // strip trailing slash
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        try {
            String messagesJson = buildMessagesJson(request);
            String payload = "{\"messages\":" + messagesJson + ",\"stream\":false}";

            traceRequest(messagesJson);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            String content = extractContent(resp.body());
            traceResponse(content);

            ModelResponse response = new ModelResponse();
            response.setContent(content);
            return response;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            traceError(e.getMessage());
            ModelResponse err = new ModelResponse();
            err.setContent("<thought>Errore di connessione a llama.cpp</thought>");
            return err;
        }
    }

    // =========================================================================
    // Prompt builder
    // =========================================================================

    /**
     * Serializes the message list into a JSON array compatible with the
     * OpenAI {@code /v1/chat/completions} format.
     */
    private String buildMessagesJson(ModelRequest request) {
        StringBuilder sb = new StringBuilder("[");

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (int i = 0; i < request.getMessages().size(); i++) {
                Message msg = request.getMessages().get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"role\":\"").append(escape(msg.getRole()))
                  .append("\",\"content\":\"").append(escape(msg.getContent()))
                  .append("\"}");
            }
        } else {
            // Fallback: system + user from flat fields
            if (request.getSystemPrompt() != null) {
                sb.append("{\"role\":\"system\",\"content\":\"")
                  .append(escape(request.getSystemPrompt())).append("\"}");
                if (request.getUserPrompt() != null) sb.append(",");
            }
            if (request.getUserPrompt() != null) {
                sb.append("{\"role\":\"user\",\"content\":\"")
                  .append(escape(request.getUserPrompt())).append("\"}");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // =========================================================================
    // Response extraction
    // =========================================================================

    private String extractContent(String body) {
        body = decodeUnicode(body);
        // llama.cpp wraps the reply in choices[0].message.content
        Matcher matcher = CONTENT_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1)
                    .replace("\\n",  "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return body;
    }

    private String decodeUnicode(String input) {
        Matcher matcher = UNICODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // =========================================================================
    // Trace helpers
    // =========================================================================

    private void traceRequest(String messagesJson) {
        String entry = """
                ================================================================================
                [%s] REQUEST  endpoint=%s/v1/chat/completions
                --------------------------------------------------------------------------------
                %s
                """.formatted(Instant.now(), baseUrl, messagesJson);
        appendTrace(entry);
    }

    private void traceResponse(String content) {
        String entry = """
                [%s] RESPONSE
                --------------------------------------------------------------------------------
                %s

                """.formatted(Instant.now(), content);
        appendTrace(entry);
    }

    private void traceError(String message) {
        String entry = "[%s] ERROR: %s%n%n".formatted(Instant.now(), message);
        appendTrace(entry);
    }

    private void appendTrace(String text) {
        try {
            Files.writeString(TRACE_FILE, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Trace failures must never propagate to the main flow
        }
    }
}
