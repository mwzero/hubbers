package org.hubbers.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebServer {
    private final RuntimeFacade runtimeFacade;
    private final ManifestFileService manifestFileService;
    private final ManifestValidator manifestValidator;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public WebServer(RuntimeFacade runtimeFacade,
                     ManifestFileService manifestFileService,
                     ManifestValidator manifestValidator) {
        this.runtimeFacade = runtimeFacade;
        this.manifestFileService = manifestFileService;
        this.manifestValidator = manifestValidator;
        this.yamlMapper = JacksonFactory.yamlMapper();
        this.jsonMapper = JacksonFactory.jsonMapper();
    }

    public Javalin start(int port) {
        Javalin app = Javalin.create(config -> {
            
            config.jetty.server(() -> {
                    QueuedThreadPool threadPool = new QueuedThreadPool();
                    return new Server(threadPool);
            });

            config.showJavalinBanner = false;
        });

        app.exception(IllegalArgumentException.class, (e, ctx) -> writeError(ctx, 400, e.getMessage()));
        app.exception(IllegalStateException.class, (e, ctx) -> writeError(ctx, 500, e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> writeError(ctx, 500, e.getMessage()));

        app.get("/", ctx -> serveStatic(ctx, "index.html", "text/html; charset=utf-8"));
        app.get("/app.js", ctx -> serveStatic(ctx, "app.js", "application/javascript; charset=utf-8"));
        app.get("/styles.css", ctx -> serveStatic(ctx, "styles.css", "text/css; charset=utf-8"));

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
        app.get("/api/agents", ctx -> ctx.json(Map.of("items", runtimeFacade.listAgents())));
        app.get("/api/tools", ctx -> ctx.json(Map.of("items", runtimeFacade.listTools())));
        app.get("/api/pipelines", ctx -> ctx.json(Map.of("items", runtimeFacade.listPipelines())));

        app.get("/api/manifest/{type}/{name}", ctx -> {
            ManifestType type = ManifestType.fromPath(ctx.pathParam("type"));
            String name = ctx.pathParam("name");
            String yaml = manifestFileService.readManifest(type, name);
            ctx.contentType("text/yaml; charset=utf-8").result(yaml);
        });

        app.post("/api/validate/{type}", ctx -> {
            ManifestType type = ManifestType.fromPath(ctx.pathParam("type"));
            String yaml = ctx.body();
            ValidationResult validation = validate(type, yaml);
            ctx.json(Map.of(
                    "valid", validation.isValid(),
                    "errors", validation.getErrors()
            ));
        });

        app.put("/api/manifest/{type}/{name}", ctx -> {
            ManifestType type = ManifestType.fromPath(ctx.pathParam("type"));
            String name = ctx.pathParam("name");
            String yaml = ctx.body();

            ValidationResult validation = validate(type, yaml);
            if (!validation.isValid()) {
                ctx.status(400).json(Map.of("saved", false, "errors", validation.getErrors()));
                return;
            }

            manifestFileService.writeManifest(type, name, yaml);
            ctx.json(Map.of("saved", true));
        });

        app.post("/api/run/{type}/{name}", ctx -> {
            ManifestType type = ManifestType.fromPath(ctx.pathParam("type"));
            String name = ctx.pathParam("name");
            JsonNode input = parseInput(ctx);
            RunResult result = run(type, name, input);
            ctx.status(result.getStatus() == ExecutionStatus.SUCCESS ? 200 : 400);
            ctx.json(toRunPayload(result));
        });

        app.start(port);
        return app;
    }

    private ValidationResult validate(ManifestType type, String yaml) {
        try {
            return switch (type) {
                case AGENT -> manifestValidator.validateAgent(yamlMapper.readValue(yaml, AgentManifest.class));
                case TOOL -> manifestValidator.validateTool(yamlMapper.readValue(yaml, ToolManifest.class));
                case PIPELINE -> manifestValidator.validatePipeline(yamlMapper.readValue(yaml, PipelineManifest.class));
            };
        } catch (IOException e) {
            ValidationResult result = ValidationResult.ok();
            result.addError("Invalid YAML: " + e.getMessage());
            return result;
        }
    }

    private JsonNode parseInput(Context ctx) {
        try {
            if (ctx.body() == null || ctx.body().isBlank()) {
                return jsonMapper.createObjectNode();
            }
            return jsonMapper.readTree(ctx.body());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid input JSON: " + e.getMessage());
        }
    }

    private RunResult run(ManifestType type, String name, JsonNode input) {
        return switch (type) {
            case AGENT -> runtimeFacade.runAgent(name, input);
            case TOOL -> runtimeFacade.runTool(name, input);
            case PIPELINE -> runtimeFacade.runPipeline(name, input);
        };
    }

    private Map<String, Object> toRunPayload(RunResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.getStatus().name());
        payload.put("output", result.getOutput());
        payload.put("error", result.getError());
        payload.put("metadata", result.getMetadata());
        return payload;
    }

    private void serveStatic(Context ctx, String fileName, String contentType) {
        String resourcePath = "web/" + fileName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                writeError(ctx, 404, "Static file not found: " + resourcePath);
                return;
            }
            ctx.contentType(contentType).result(is.readAllBytes());
        } catch (IOException e) {
            writeError(ctx, 500, "Cannot serve static file: " + fileName);
        }
    }

    private void writeError(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of(
                "status", "ERROR",
                "message", message == null ? "Unexpected error" : message
        ));
    }
}
