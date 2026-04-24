package org.hubbers.tool.builtin;

import org.hubbers.tool.ToolDriver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ShellExecToolDriver implements ToolDriver {
    private final ObjectMapper mapper;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /** Patterns that are always blocked to prevent destructive operations. */
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("\\brm\\s+-rf\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmkfs\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdd\\s+if=", Pattern.CASE_INSENSITIVE),
            Pattern.compile(":\\(\\)\\{\\s*:\\|:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bformat\\s+[a-z]:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(shutdown|reboot|halt|poweroff)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile(">\\s*/dev/sd[a-z]", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String type() {
        return "shell.exec";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String command = input.path("command").asText();
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: command");
        }

        // Block dangerous patterns
        validateCommandSafety(command);

        // Validate against allowed-commands whitelist if configured
        if (manifest != null && manifest.getConfig() != null) {
            Object allowedCmds = manifest.getConfig().get("allowed_commands");
            if (allowedCmds instanceof List<?> patterns) {
                validateAllowedCommands(command, patterns);
            }
        }

        List<String> cmdList = buildCommandList(command, input.path("args"));
        String workingDir = input.path("workingDir").asText("");
        JsonNode envNode = input.path("env");
        int timeoutSeconds = resolveTimeout(manifest, input);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            
            // Set working directory if provided
            if (!workingDir.isEmpty()) {
                Path workDir = Path.of(workingDir);
                if (workingDir.contains("..")) {
                    throw new SecurityException("Path traversal not allowed in workingDir: " + workingDir);
                }
                pb.directory(workDir.toFile());
            }

            // Merge environment variables
            if (envNode.isObject()) {
                Map<String, String> env = pb.environment();
                envNode.fields().forEachRemaining(entry -> 
                    env.put(entry.getKey(), entry.getValue().asText())
                );
            }

            // Start process and capture output
            Process process = pb.start();
            
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            boolean timedOut = !completed;
            
            String stdout = "";
            String stderr = "";
            int exitCode = -1;
            
            if (timedOut) {
                process.destroyForcibly();
                stdout = new String(process.getInputStream().readAllBytes());
                stderr = new String(process.getErrorStream().readAllBytes());
            } else {
                stdout = new String(process.getInputStream().readAllBytes());
                stderr = new String(process.getErrorStream().readAllBytes());
                exitCode = process.exitValue();
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("exitCode", exitCode);
            output.put("stdout", stdout);
            output.put("stderr", stderr);
            output.put("timedOut", timedOut);
            return output;
            
        } catch (IOException e) {
            throw new IllegalStateException("Failed to execute command: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command execution interrupted: " + command, e);
        }
    }

    /**
     * Validate that the command does not match any blocked dangerous patterns.
     *
     * @param command the command string to validate
     * @throws SecurityException if a dangerous pattern is detected
     */
    private void validateCommandSafety(String command) {
        for (Pattern blocked : BLOCKED_PATTERNS) {
            if (blocked.matcher(command).find()) {
                log.warn("Blocked dangerous command pattern '{}' in: {}", blocked.pattern(), command);
                throw new SecurityException("Command blocked by security policy: matches dangerous pattern");
            }
        }
    }

    /**
     * Validate that the command matches at least one of the allowed command patterns.
     *
     * @param command the command string to validate
     * @param patterns the allowed regex patterns
     * @throws SecurityException if the command doesn't match any allowed pattern
     */
    private void validateAllowedCommands(String command, List<?> patterns) {
        boolean matched = patterns.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(pattern -> Pattern.matches(pattern, command));
        if (!matched) {
            log.warn("Command '{}' does not match any allowed pattern", command);
            throw new SecurityException("Command not in allowed commands whitelist");
        }
    }

    private List<String> buildCommandList(String command, JsonNode argsNode) {
        List<String> cmdList = new ArrayList<>();
        
        // Platform-specific shell wrapper
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            cmdList.add("cmd.exe");
            cmdList.add("/c");
        } else {
            cmdList.add("sh");
            cmdList.add("-c");
        }
        
        // Build command string
        StringBuilder fullCommand = new StringBuilder(command);
        if (argsNode.isArray()) {
            for (JsonNode arg : argsNode) {
                fullCommand.append(" ").append(escapeArg(arg.asText()));
            }
        }
        
        cmdList.add(fullCommand.toString());
        return cmdList;
    }

    private String escapeArg(String arg) {
        // Basic escaping for shell arguments
        if (arg.contains(" ") || arg.contains("\"") || arg.contains("'")) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                return "\"" + arg.replace("\"", "\\\"") + "\"";
            } else {
                return "'" + arg.replace("'", "'\\''") + "'";
            }
        }
        return arg;
    }

    private int resolveTimeout(ToolManifest manifest, JsonNode input) {
        JsonNode timeoutNode = input.get("timeoutSeconds");
        if (timeoutNode != null && timeoutNode.isNumber()) {
            return timeoutNode.asInt();
        }
        
        Object configured = manifest.getConfig() == null ? null : manifest.getConfig().get("default_timeout_seconds");
        if (configured != null) {
            try {
                return Integer.parseInt(configured.toString());
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        
        return DEFAULT_TIMEOUT_SECONDS;
    }
}
