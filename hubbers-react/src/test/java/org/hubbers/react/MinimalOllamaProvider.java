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
import java.util.regex.Pattern;

import org.hubbers.react.model.Message;
import org.hubbers.react.model.ModelProvider;
import org.hubbers.react.model.ModelRequest;
import org.hubbers.react.model.ModelResponse;

/**
 * Minimal Ollama HTTP provider for standalone demo use.
 * All requests and responses are traced to {@code ollama-trace.log} in the working directory.
 * Production code should use {@code OllamaModelProvider} from {@code hubbers-core}.
 */
public class MinimalOllamaProvider implements ModelProvider {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
            "\"response\"\\s*:\\s*\"(.*?)\"\\s*(,\\s*\"[a-zA-Z0-9_-]+\"\\s*:)", Pattern.DOTALL);
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    private static final Path TRACE_FILE = Paths.get("ollama-trace.log");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    MinimalOllamaProvider() {}

    @Override
    public ModelResponse generate(ModelRequest request) {
        try {
            String prompt  = buildPrompt(request);
            String escaped = prompt.replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n")
                                    .replace("\r", "\\r");
            String payload = "{\"model\":\"" + request.getModel() + "\",\"prompt\":\"" + escaped + "\",\"stream\":false}";

            traceRequest(request.getModel(), prompt);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
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
            err.setContent("<thought>Errore di connessione a Ollama</thought>");
            return err;
        }
    }

    // =========================================================================
    // Trace helpers
    // =========================================================================

    private void traceRequest(String model, String prompt) {
        String entry = """
                ================================================================================
                [%s] REQUEST  model=%s
                --------------------------------------------------------------------------------
                %s
                """.formatted(Instant.now(), model, prompt);
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

    // =========================================================================
    // Prompt / response helpers
    // =========================================================================

    private String buildPrompt(ModelRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Message msg : request.getMessages()) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            return sb.toString();
        }
        String sys  = request.getSystemPrompt() != null ? request.getSystemPrompt() + "\n" : "";
        String user = request.getUserPrompt()   != null ? request.getUserPrompt()            : "";
        return sys + user;
    }

    private String extractContent(String body) {
        body = decodeUnicode(body);
        java.util.regex.Matcher matcher = RESPONSE_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1)
                            .replace("\\n",  "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
        }
        return body;
    }

    private String decodeUnicode(String input) {
        java.util.regex.Matcher matcher = UNICODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
