package org.hubbers.tool.builtin;

import org.hubbers.tool.ToolDriver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RequiredArgsConstructor
public class DockerToolDriver implements ToolDriver {
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "docker";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String image = manifest.getConfig().get("image").toString();
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("hubbers-tool-");
            Path inputPath = tmpDir.resolve("input.json");
            Path outputPath = tmpDir.resolve("output.json");
            mapper.writeValue(inputPath.toFile(), input);

            List<String> cmd = List.of(
                    "docker", "run", "--rm",
                    "-v", tmpDir.toAbsolutePath() + ":/workspace",
                    image
            );
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String log = new String(process.getInputStream().readAllBytes());
                throw new IllegalStateException("Docker tool failed with code " + exitCode + ": " + log);
            }
            if (!Files.exists(outputPath)) {
                throw new IllegalStateException("Docker tool did not produce /workspace/output.json");
            }
            return mapper.readTree(outputPath.toFile());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker tool execution failed", e);
        } finally {
            if (tmpDir != null) {
                try (var s = Files.walk(tmpDir)) {
                    s.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }
    }
}
