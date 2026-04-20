package org.hubbers.skill.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Executes skills in script mode.
 * Directly executes scripts from the scripts/ directory.
 * Delegates to shell.exec or process.manage tools for execution.
 */
public class ScriptExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(ScriptExecutionHandler.class);
    
    private final ToolExecutor toolExecutor;
    private final org.hubbers.app.ArtifactRepository artifactRepository;
    private final ObjectMapper mapper;

    public ScriptExecutionHandler(ToolExecutor toolExecutor, 
                                   org.hubbers.app.ArtifactRepository artifactRepository,
                                   ObjectMapper mapper) {
        this.toolExecutor = toolExecutor;
        this.artifactRepository = artifactRepository;
        this.mapper = mapper;
    }

    /**
     * Execute skill by running scripts from scripts/ directory.
     * 
     * @param manifest Skill manifest containing scripts
     * @param input User input as JSON
     * @return RunResult with script execution output
     */
    public RunResult execute(SkillManifest manifest, JsonNode input) {
        log.debug("Executing skill '{}' in script mode", manifest.getName());

        if (!manifest.hasScripts()) {
            return RunResult.failed(
                "Skill '" + manifest.getName() + "' has no scripts to execute. " +
                "Check scripts/ directory exists and contains executable files."
            );
        }

        // Find the main script (look for common entry points)
        Path mainScript = findMainScript(manifest);
        if (mainScript == null) {
            return RunResult.failed(
                "No main script found in skill '" + manifest.getName() + "'. " +
                "Expected: main.py, main.sh, run.py, run.sh, or similar."
            );
        }

        try {
            // Execute script using shell.exec tool
            RunResult result = executeScript(mainScript, input);
            log.debug("Script execution completed for skill '{}'", manifest.getName());
            return result;
        } catch (Exception e) {
            log.error("Script execution failed for skill '{}'", manifest.getName(), e);
            return RunResult.failed("Script execution error: " + e.getMessage());
        }
    }

    private Path findMainScript(SkillManifest manifest) {
        // Look for common script entry point names
        String[] commonNames = {
            "main.py", "run.py", "execute.py",
            "main.sh", "run.sh", "execute.sh",
            "main.js", "run.js", "index.js"
        };

        for (Path script : manifest.getScripts()) {
            String fileName = script.getFileName().toString();
            for (String commonName : commonNames) {
                if (fileName.equals(commonName)) {
                    return script;
                }
            }
        }

        // If no common name found, use first script
        return manifest.getScripts().isEmpty() ? null : manifest.getScripts().get(0);
    }

    private RunResult executeScript(Path scriptPath, JsonNode input) {
        try {
            // Build shell.exec tool input
            ObjectNode toolInput = mapper.createObjectNode();
            toolInput.put("command", buildCommand(scriptPath, input));
            toolInput.put("workingDir", scriptPath.getParent().toString());

            // Execute via shell.exec tool
            org.hubbers.manifest.tool.ToolManifest shellExecManifest = artifactRepository.loadTool("shell.exec");
            return toolExecutor.execute(shellExecManifest, toolInput);
        } catch (Exception e) {
            return RunResult.failed("Failed to execute script: " + e.getMessage());
        }
    }

    private String buildCommand(Path scriptPath, JsonNode input) {
        String scriptName = scriptPath.toString();
        
        // Determine script type and build appropriate command
        if (scriptName.endsWith(".py")) {
            // Python script - pass input as JSON argument
            return "python \"" + scriptName + "\" '" + input.toString() + "'";
        } else if (scriptName.endsWith(".sh")) {
            // Shell script - pass input as JSON argument
            return "bash \"" + scriptName + "\" '" + input.toString() + "'";
        } else if (scriptName.endsWith(".js")) {
            // JavaScript script - pass input as JSON argument
            return "node \"" + scriptName + "\" '" + input.toString() + "'";
        } else {
            // Generic execution
            return "\"" + scriptName + "\" '" + input.toString() + "'";
        }
    }
}
