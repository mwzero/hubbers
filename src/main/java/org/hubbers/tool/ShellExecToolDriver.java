package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ShellExecToolDriver implements ToolDriver {
    private final ObjectMapper mapper;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    public ShellExecToolDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

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
