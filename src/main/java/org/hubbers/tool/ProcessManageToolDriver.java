package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Stream;

public class ProcessManageToolDriver implements ToolDriver {
    private final ObjectMapper mapper;
    private static final int DEFAULT_MAX_PROCESSES = 1000;

    public ProcessManageToolDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "process.manage";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String operation = input.path("operation").asText();
        if (operation.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: operation");
        }

        return switch (operation) {
            case "list" -> list(manifest, input.path("namePattern").asText(""));
            case "info" -> info(input.path("pid").asLong(-1));
            case "kill" -> kill(input.path("pid").asLong(-1), input.path("force").asBoolean(false));
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    private JsonNode list(ToolManifest manifest, String namePattern) {
        try {
            int maxProcesses = resolveMaxProcesses(manifest);
            ArrayNode processes = mapper.createArrayNode();
            
            Stream<ProcessHandle> stream = ProcessHandle.allProcesses();
            if (!namePattern.isEmpty()) {
                stream = stream.filter(ph -> {
                    Optional<String> cmd = ph.info().command();
                    return cmd.isPresent() && cmd.get().toLowerCase().contains(namePattern.toLowerCase());
                });
            }
            
            stream.limit(maxProcesses).forEach(ph -> {
                ObjectNode processInfo = mapper.createObjectNode();
                processInfo.put("pid", ph.pid());
                
                ProcessHandle.Info info = ph.info();
                info.command().ifPresent(cmd -> processInfo.put("command", cmd));
                info.user().ifPresent(user -> processInfo.put("user", user));
                
                processes.add(processInfo);
            });
            
            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.set("processes", processes);
            return output;
            
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list processes", e);
        }
    }

    private JsonNode info(long pid) {
        if (pid < 0) {
            throw new IllegalArgumentException("Missing required field: pid");
        }
        
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) {
            throw new IllegalArgumentException("Process not found: " + pid);
        }
        
        ProcessHandle ph = processHandle.get();
        ProcessHandle.Info info = ph.info();
        
        ObjectNode infoNode = mapper.createObjectNode();
        infoNode.put("pid", ph.pid());
        
        info.command().ifPresent(cmd -> infoNode.put("command", cmd));
        
        info.arguments().ifPresent(args -> {
            ArrayNode argsArray = mapper.createArrayNode();
            for (String arg : args) {
                argsArray.add(arg);
            }
            infoNode.set("arguments", argsArray);
        });
        
        info.startInstant().ifPresent(start -> 
            infoNode.put("startTime", DateTimeFormatter.ISO_INSTANT.format(
                start.atOffset(ZoneOffset.UTC)))
        );
        
        info.user().ifPresent(user -> infoNode.put("user", user));
        
        ObjectNode output = mapper.createObjectNode();
        output.put("success", true);
        output.set("info", infoNode);
        return output;
    }

    private JsonNode kill(long pid, boolean force) {
        if (pid < 0) {
            throw new IllegalArgumentException("Missing required field: pid");
        }
        
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) {
            throw new IllegalArgumentException("Process not found: " + pid);
        }
        
        ProcessHandle ph = processHandle.get();
        boolean destroyed;
        
        if (force) {
            destroyed = ph.destroyForcibly();
        } else {
            destroyed = ph.destroy();
        }
        
        if (!destroyed) {
            throw new IllegalStateException("Failed to terminate process: " + pid);
        }
        
        ObjectNode output = mapper.createObjectNode();
        output.put("success", true);
        output.put("message", "Process " + pid + (force ? " forcefully terminated" : " terminated"));
        return output;
    }

    private int resolveMaxProcesses(ToolManifest manifest) {
        Object configured = manifest.getConfig() == null ? null : manifest.getConfig().get("max_process_list");
        if (configured != null) {
            try {
                return Integer.parseInt(configured.toString());
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return DEFAULT_MAX_PROCESSES;
    }
}
