package org.hubbers.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.nlp.NaturalLanguageTaskService;
import org.hubbers.nlp.TaskExecutionResult;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class WebServer {
    private final RuntimeFacade runtimeFacade;
    private final ManifestFileService manifestFileService;
    private final ManifestValidator manifestValidator;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    private final NaturalLanguageTaskService taskService;

    public WebServer(RuntimeFacade runtimeFacade,
                     ManifestFileService manifestFileService,
                     ManifestValidator manifestValidator,
                     NaturalLanguageTaskService taskService) {
        this.runtimeFacade = runtimeFacade;
        this.manifestFileService = manifestFileService;
        this.manifestValidator = manifestValidator;
        this.taskService = taskService;
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
        
        // Serve all files from assets folder statically
        app.get("/assets/*", ctx -> {
            String path = ctx.path().substring(1); // Remove leading slash
            String contentType = getContentType(path);
            serveStatic(ctx, path, contentType);
        });

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
            
            // Check if manifest has a "before" form
            var formTrigger = getFormTrigger(type, name);
            if (formTrigger != null && formTrigger.getBefore() != null) {
                // Create form session instead of running immediately
                var formSession = runtimeFacade.getFormService().createFormSession(
                    null, // execution ID will be created after form submission
                    type.name().toLowerCase(),
                    name,
                    formTrigger.getBefore(),
                    org.hubbers.forms.FormStage.BEFORE,
                    input
                );
                
                ctx.status(202); // Accepted - awaiting form input
                ctx.json(Map.of(
                    "requiresForm", true,
                    "formSessionId", formSession.getSessionId(),
                    "form", runtimeFacade.getFormService().toFrontendFormat(formTrigger.getBefore())
                ));
                return;
            }
            
            // No form required, execute normally
            RunResult result = run(type, name, input);
            ctx.status(result.getStatus() == ExecutionStatus.SUCCESS ? 200 : 400);
            ctx.json(toRunPayload(result));
        });

        // Submit form and execute
        app.post("/api/forms/{sessionId}/submit", ctx -> {
            String sessionId = ctx.pathParam("sessionId");
            var formDataMap = jsonMapper.readValue(ctx.body(), Map.class);
            
            var session = runtimeFacade.getFormService().submitForm(sessionId, formDataMap);
            
            // Merge form data with original input and execute
            var mergedInput = runtimeFacade.getFormService().mergeFormDataWithInput(
                session.getOriginalInput(), 
                session.getFormData()
            );
            
            RunResult result = run(
                ManifestType.fromPath(session.getArtifactType()),
                session.getArtifactName(),
                mergedInput
            );
            
            ctx.status(result.getStatus() == ExecutionStatus.SUCCESS ? 200 : 400);
            ctx.json(toRunPayload(result));
        });

        // Get form session details
        app.get("/api/forms/{sessionId}", ctx -> {
            String sessionId = ctx.pathParam("sessionId");
            var session = runtimeFacade.getFormService().getSession(sessionId);
            if (session == null) {
                writeError(ctx, 404, "Form session not found");
                return;
            }
            ctx.json(session);
        });

        // Cancel form session
        app.delete("/api/forms/{sessionId}", ctx -> {
            String sessionId = ctx.pathParam("sessionId");
            runtimeFacade.getFormService().cancelSession(sessionId);
            ctx.json(Map.of("cancelled", true));
        });

        // Execution history endpoints
        app.get("/api/executions", ctx -> {
            try {
                var executions = runtimeFacade.getExecutionStorage().listExecutions();
                var executionList = executions.stream()
                    .map(executionId -> {
                        try {
                            String metadataJson = runtimeFacade.getExecutionStorage().readFile(executionId, "execution-metadata.json");
                            return jsonMapper.readTree(metadataJson);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(metadata -> metadata != null)
                    .toList();
                
                ctx.json(Map.of("items", executionList));
            } catch (IOException e) {
                writeError(ctx, 500, "Failed to list executions: " + e.getMessage());
            }
        });

        app.get("/api/executions/{execution_id}", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            try {
                String metadataJson = runtimeFacade.getExecutionStorage().readFile(executionId, "execution-metadata.json");
                JsonNode metadata = jsonMapper.readTree(metadataJson);
                ctx.json(metadata);
            } catch (IOException e) {
                writeError(ctx, 404, "Execution not found: " + executionId);
            }
        });

        app.get("/api/executions/{execution_id}/log", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            try {
                String log = runtimeFacade.getExecutionStorage().readFile(executionId, "execution-log.txt");
                ctx.contentType("text/plain; charset=utf-8").result(log);
            } catch (IOException e) {
                writeError(ctx, 404, "Log not found for execution: " + executionId);
            }
        });

        app.get("/api/executions/{execution_id}/input", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            try {
                String inputJson = runtimeFacade.getExecutionStorage().readFile(executionId, "input.json");
                JsonNode input = jsonMapper.readTree(inputJson);
                ctx.json(input);
            } catch (IOException e) {
                writeError(ctx, 404, "Input not found for execution: " + executionId);
            }
        });

        app.get("/api/executions/{execution_id}/output", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            try {
                String outputJson = runtimeFacade.getExecutionStorage().readFile(executionId, "output.json");
                JsonNode output = jsonMapper.readTree(outputJson);
                ctx.json(output);
            } catch (IOException e) {
                writeError(ctx, 404, "Output not found for execution: " + executionId);
            }
        });

        app.get("/api/executions/{execution_id}/steps", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            try {
                var executionPath = runtimeFacade.getExecutionStorage().getExecutionPath(executionId);
                var stepsPath = executionPath.resolve("steps");
                
                if (!java.nio.file.Files.exists(stepsPath)) {
                    ctx.json(Map.of("items", java.util.List.of()));
                    return;
                }
                
                var stepDirs = java.nio.file.Files.list(stepsPath)
                    .filter(java.nio.file.Files::isDirectory)
                    .sorted()
                    .map(path -> {
                        String stepName = path.getFileName().toString();
                        Map<String, Object> stepInfo = new LinkedHashMap<>();
                        stepInfo.put("name", stepName);
                        stepInfo.put("path", "steps/" + stepName);
                        
                        // Try to read step metadata if available
                        try {
                            String stepInputPath = executionId + "/steps/" + stepName;
                            String inputJson = runtimeFacade.getExecutionStorage().readFile(stepInputPath, "input.json");
                            stepInfo.put("hasInput", true);
                        } catch (IOException e) {
                            stepInfo.put("hasInput", false);
                        }
                        
                        try {
                            String stepOutputPath = executionId + "/steps/" + stepName;
                            String outputJson = runtimeFacade.getExecutionStorage().readFile(stepOutputPath, "output.json");
                            stepInfo.put("hasOutput", true);
                        } catch (IOException e) {
                            stepInfo.put("hasOutput", false);
                        }
                        
                        return stepInfo;
                    })
                    .toList();
                
                ctx.json(Map.of("items", stepDirs));
            } catch (IOException e) {
                writeError(ctx, 500, "Failed to list steps: " + e.getMessage());
            }
        });

        app.get("/api/executions/{execution_id}/steps/{step_name}/log", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            String stepName = ctx.pathParam("step_name");
            try {
                String stepPath = executionId + "/steps/" + stepName;
                String log = runtimeFacade.getExecutionStorage().readFile(stepPath, "execution-log.txt");
                ctx.contentType("text/plain; charset=utf-8").result(log);
            } catch (IOException e) {
                writeError(ctx, 404, "Step log not found");
            }
        });

        app.get("/api/executions/{execution_id}/steps/{step_name}/input", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            String stepName = ctx.pathParam("step_name");
            try {
                String stepPath = executionId + "/steps/" + stepName;
                String inputJson = runtimeFacade.getExecutionStorage().readFile(stepPath, "input.json");
                JsonNode input = jsonMapper.readTree(inputJson);
                ctx.json(input);
            } catch (IOException e) {
                writeError(ctx, 404, "Step input not found");
            }
        });

        app.get("/api/executions/{execution_id}/steps/{step_name}/output", ctx -> {
            String executionId = ctx.pathParam("execution_id");
            String stepName = ctx.pathParam("step_name");
            try {
                String stepPath = executionId + "/steps/" + stepName;
                String outputJson = runtimeFacade.getExecutionStorage().readFile(stepPath, "output.json");
                JsonNode output = jsonMapper.readTree(outputJson);
                ctx.json(output);
            } catch (IOException e) {
                writeError(ctx, 404, "Step output not found");
            }
        });

                // Execute a new task
        app.post("/api/task/execute", ctx -> {
            try {
                JsonNode body = jsonMapper.readTree(ctx.body());
                String request = body.get("request").asText();
                JsonNode context = body.has("context") ? body.get("context") : null;
                
                log.info("POST /api/task/execute - request: {}", request);
                
                TaskExecutionResult result = taskService.executeTask(request, context);
                
                ctx.json(result);
                ctx.status(result.isSuccess() ? 200 : 500);
                
            } catch (Exception e) {
                log.error("Task execution failed", e);
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });
        
        // Continue an existing conversation
        app.post("/api/task/continue", ctx -> {
            try {
                JsonNode body = jsonMapper.readTree(ctx.body());
                String request = body.get("request").asText();
                String conversationId = body.get("conversationId").asText();
                JsonNode context = body.has("context") ? body.get("context") : null;
                
                log.info("POST /api/task/continue - conversation: {}, request: {}", 
                    conversationId, request);
                
                TaskExecutionResult result = taskService.executeTaskWithConversation(
                    request, context, conversationId);
                
                ctx.json(result);
                ctx.status(result.isSuccess() ? 200 : 500);
                
            } catch (Exception e) {
                log.error("Task continuation failed", e);
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });
        
        // Get task service info
        app.get("/api/task/info", ctx -> {
            Map<String, Object> info = new HashMap<>();
            info.put("available_tools", taskService.getAvailableToolsCount());
            info.put("agent", "universal.task");
            info.put("status", "ready");
            ctx.json(info);
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
    
    private org.hubbers.forms.FormTrigger getFormTrigger(ManifestType type, String name) {
        try {
            return switch (type) {
                case AGENT -> {
                    var manifest = runtimeFacade.getArtifactRepository().loadAgent(name);
                    yield manifest.getForms();
                }
                case TOOL -> {
                    var manifest = runtimeFacade.getArtifactRepository().loadTool(name);
                    yield manifest.getForms();
                }
                case PIPELINE -> {
                    var manifest = runtimeFacade.getArtifactRepository().loadPipeline(name);
                    yield manifest.getForms();
                }
            };
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toRunPayload(RunResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", result.getExecutionId());
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
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".ttf")) return "font/ttf";
        if (path.endsWith(".eot")) return "application/vnd.ms-fontobject";
        return "application/octet-stream";
    }

    private void writeError(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of(
                "status", "ERROR",
                "message", message == null ? "Unexpected error" : message
        ));
    }
}
