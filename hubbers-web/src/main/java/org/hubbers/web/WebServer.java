package org.hubbers.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.config.AppConfig;
import org.hubbers.config.ConfigLoader;
import org.hubbers.config.RepoPathResolver;
import org.hubbers.config.SecurityConfig;
import org.hubbers.execution.ExecutionStatus;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.mcp.McpRequestHandler;
import org.hubbers.mcp.protocol.McpRequest;
import org.hubbers.mcp.protocol.McpResponse;
import org.hubbers.util.JacksonFactory;
import org.hubbers.validation.ManifestValidator;
import org.hubbers.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WebServer {
    private final RuntimeFacade runtimeFacade;
    private final ManifestFileService manifestFileService;
    private final ManifestValidator manifestValidator;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private final String repoPath;
    private McpRequestHandler mcpRequestHandler;
    private OpenAiCompatibleProxy openAiProxy;
    private McpSseTransport mcpSseTransport;

    public WebServer(RuntimeFacade runtimeFacade,
                     ManifestFileService manifestFileService,
                     ManifestValidator manifestValidator) {
        this(runtimeFacade, manifestFileService, manifestValidator, RepoPathResolver.DEFAULT_REPO_PATH);
    }

    public WebServer(RuntimeFacade runtimeFacade,
                     ManifestFileService manifestFileService,
                     ManifestValidator manifestValidator,
                     String repoPath) {
        this.runtimeFacade = runtimeFacade;
        this.manifestFileService = manifestFileService;
        this.manifestValidator = manifestValidator;
        this.yamlMapper = JacksonFactory.yamlMapper();
        this.jsonMapper = JacksonFactory.jsonMapper();
        this.repoPath = repoPath;
    }

    /**
     * Sets the MCP request handler for Model Context Protocol support.
     *
     * @param mcpRequestHandler the MCP handler to use for /mcp endpoint
     */
    public void setMcpRequestHandler(McpRequestHandler mcpRequestHandler) {
        this.mcpRequestHandler = mcpRequestHandler;
    }

    /**
     * Sets the OpenAI-compatible proxy for /v1/* endpoints.
     *
     * @param openAiProxy the proxy to use for OpenAI-compatible function calling
     */
    public void setOpenAiProxy(OpenAiCompatibleProxy openAiProxy) {
        this.openAiProxy = openAiProxy;
    }

    public Javalin start(int port) {
        Javalin app = Javalin.create(config -> {
            
            config.jetty.server(() -> {
                    QueuedThreadPool threadPool = new QueuedThreadPool();
                    return new Server(threadPool);
            });

            config.showJavalinBanner = false;

            // Registrazione del plugin CORS
            config.plugins.enableCors(cors -> {
                cors.add(it -> {
                    it.allowHost("http://127.0.0.1:8080");
                });
            });
        });

        app.exception(IllegalArgumentException.class, (e, ctx) -> writeError(ctx, 400, e.getMessage()));
        app.exception(IllegalStateException.class, (e, ctx) -> writeError(ctx, 500, e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> writeError(ctx, 500, e.getMessage()));

        // API key authentication middleware
        app.before("/api/*", ctx -> {
            // Skip auth for health check and static settings endpoints
            String path = ctx.path();
            if ("/api/health".equals(path)) {
                return;
            }
            try {
                ConfigLoader configLoader = new ConfigLoader(repoPath);
                AppConfig config = configLoader.loadRaw();
                SecurityConfig security = config.getSecurity();
                if (security != null && security.getApiKey() != null && !security.getApiKey().isBlank()) {
                    String authHeader = ctx.header("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        ctx.status(401).json(Map.of("error", "Unauthorized: missing Bearer token"));
                        return;
                    }
                    String token = authHeader.substring("Bearer ".length());
                    if (!security.getApiKey().equals(token)) {
                        ctx.status(401).json(Map.of("error", "Unauthorized: invalid API key"));
                        return;
                    }
                }
            } catch (Exception e) {
                // If config can't be loaded, allow request (no security config = open)
                log.debug("Could not load security config, allowing request: {}", e.getMessage());
            }
        });

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
        
        // Settings endpoints
        app.get("/api/settings", ctx -> {
            try {
                ConfigLoader configLoader = new ConfigLoader(repoPath);
                AppConfig config = configLoader.loadRaw();
                ctx.json(config);
            } catch (Exception e) {
                log.error("Failed to load settings", e);
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });
        
        app.post("/api/settings", ctx -> {
            try {
                AppConfig config = jsonMapper.readValue(ctx.body(), AppConfig.class);
                ConfigLoader configLoader = new ConfigLoader(repoPath);
                configLoader.save(config);
                ctx.json(Map.of("success", true, "message", "Settings saved successfully"));
            } catch (Exception e) {
                log.error("Failed to save settings", e);
                ctx.status(500).json(Map.of("success", false, "error", e.getMessage()));
            }
        });
        
        // Model listing endpoint — queries Ollama /api/tags for locally available models
        app.get("/api/models", ctx -> {
            try {
                ConfigLoader configLoader = new ConfigLoader(repoPath);
                AppConfig config = configLoader.loadRaw();
                var models = new java.util.ArrayList<Map<String, Object>>();

                // Query Ollama for local models
                String ollamaUrl = config.getOllama() != null && config.getOllama().getBaseUrl() != null
                        ? config.getOllama().getBaseUrl() : "http://localhost:11434";
                try {
                    var client = java.net.http.HttpClient.newHttpClient();
                    var request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(ollamaUrl + "/api/tags"))
                            .timeout(java.time.Duration.ofSeconds(5))
                            .GET().build();
                    var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        JsonNode body = jsonMapper.readTree(response.body());
                        if (body.has("models")) {
                            for (JsonNode m : body.get("models")) {
                                models.add(Map.of(
                                    "provider", "ollama",
                                    "name", m.get("name").asText(),
                                    "size", m.has("size") ? m.get("size").asLong() : 0
                                ));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Ollama not available: {}", e.getMessage());
                }

                // Add configured cloud providers as options
                if (config.getOpenai() != null && config.getOpenai().getApiKey() != null) {
                    String defaultModel = config.getOpenai().getDefaultModel() != null
                            ? config.getOpenai().getDefaultModel() : "gpt-4.1-mini";
                    models.add(Map.of("provider", "openai", "name", defaultModel, "size", 0));
                }
                if (config.getAnthropic() != null && config.getAnthropic().getApiKey() != null) {
                    models.add(Map.of("provider", "anthropic", "name", "claude-sonnet-4-20250514", "size", 0));
                }
                if (config.getLlamaCpp() != null) {
                    models.add(Map.of("provider", "llama-cpp", "name", "default", "size", 0));
                }

                ctx.json(Map.of("models", models));
            } catch (Exception e) {
                log.error("Failed to list models", e);
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });

        app.get("/api/agents", ctx -> ctx.json(Map.of("items", runtimeFacade.listAgents())));
        app.get("/api/tools", ctx -> ctx.json(Map.of("items", runtimeFacade.listTools())));
        app.get("/api/pipelines", ctx -> ctx.json(Map.of("items", runtimeFacade.listPipelines())));
        app.get("/api/skills", ctx -> ctx.json(Map.of("items", runtimeFacade.listSkills())));

        app.get("/api/catalog/drivers", ctx -> ctx.json(Map.of("drivers", toolDriverCatalog())));

        // ── Bruno collection browser ──
        // List Bruno projects and requests so the UI can build tool configs.
        app.get("/api/bruno", ctx -> {
            try {
                java.nio.file.Path brunoRoot = java.nio.file.Path.of(repoPath, "bruno");
                if (!java.nio.file.Files.isDirectory(brunoRoot)) {
                    ctx.json(Map.of("projects", List.of()));
                    return;
                }
                var projects = java.nio.file.Files.list(brunoRoot)
                        .filter(java.nio.file.Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .toList();
                ctx.json(Map.of("projects", projects));
            } catch (java.io.IOException e) {
                writeError(ctx, 500, "Failed to list Bruno projects: " + e.getMessage());
            }
        });

        app.get("/api/bruno/{project}", ctx -> {
            String project = ctx.pathParam("project");
            // Validate: only allow safe names (no path traversal)
            if (!project.matches("[A-Za-z0-9 ._-]+")) {
                writeError(ctx, 400, "Invalid project name");
                return;
            }
            try {
                java.nio.file.Path projectDir = java.nio.file.Path.of(repoPath, "bruno", project);
                if (!java.nio.file.Files.isDirectory(projectDir)) {
                    writeError(ctx, 404, "Bruno project not found: " + project);
                    return;
                }
                // Walk the project dir and collect all *.yml files (excluding opencollection.yml and environments/)
                java.nio.file.Path rootNorm = projectDir.toAbsolutePath().normalize();
                var requests = java.nio.file.Files.walk(projectDir)
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".yml"))
                        .filter(p -> !p.getFileName().toString().equals("opencollection.yml"))
                        .filter(p -> !p.toAbsolutePath().normalize().toString().contains("environments"))
                        .map(p -> {
                            String rel = rootNorm.relativize(p.toAbsolutePath().normalize()).toString();
                            // Remove .yml extension and normalise separators
                            String reqPath = rel.endsWith(".yml") ? rel.substring(0, rel.length() - 4) : rel;
                            reqPath = reqPath.replace("\\", "/");
                            return Map.of("path", reqPath, "name", p.getFileName().toString().replace(".yml", ""));
                        })
                        .sorted((a, b) -> ((String) a.get("path")).compareTo((String) b.get("path")))
                        .toList();
                ctx.json(Map.of("project", project, "requests", requests));
            } catch (java.io.IOException e) {
                writeError(ctx, 500, "Failed to list Bruno requests: " + e.getMessage());
            }
        });

        // Fetch the structured content of a single Bruno request file.
        // Uses a dedicated route to avoid conflict with /api/bruno/{project} wildcard.
        // Query params: project=, path=
        app.get("/api/bruno-request", ctx -> {
            String project = ctx.queryParam("project");
            if (project == null || project.isBlank()) {
                writeError(ctx, 400, "Missing 'project' query parameter");
                return;
            }
            if (!project.matches("[A-Za-z0-9 ._-]+")) {
                writeError(ctx, 400, "Invalid project name");
                return;
            }
            String requestPath = ctx.queryParam("path");
            if (requestPath == null || requestPath.isBlank()) {
                writeError(ctx, 400, "Missing 'path' query parameter");
                return;
            }
            // Prevent path traversal
            if (requestPath.contains("..")) {
                writeError(ctx, 400, "Invalid request path");
                return;
            }
            try {
                java.nio.file.Path projectDir = java.nio.file.Path.of(repoPath, "bruno", project).toAbsolutePath().normalize();
                java.nio.file.Path requestFile = projectDir.resolve(requestPath.replace("/", java.io.File.separator) + ".yml").normalize();
                if (!requestFile.startsWith(projectDir)) {
                    writeError(ctx, 400, "Invalid request path");
                    return;
                }
                if (!java.nio.file.Files.exists(requestFile)) {
                    writeError(ctx, 404, "Request file not found: " + requestPath);
                    return;
                }
                String raw = java.nio.file.Files.readString(requestFile);
                JsonNode node = JacksonFactory.yamlMapper().readTree(raw);

                var result = new java.util.LinkedHashMap<String, Object>();
                result.put("project", project);
                result.put("path", requestPath);
                result.put("name", node.path("info").path("name").asText(requestFile.getFileName().toString().replace(".yml", "")));
                result.put("method", node.path("http").path("method").asText("GET").toUpperCase());
                result.put("url", node.path("http").path("url").asText(""));
                result.put("auth", node.path("http").path("auth").asText(""));
                // params
                var params = new java.util.ArrayList<Map<String, String>>();
                JsonNode paramsNode = node.path("http").path("params");
                if (paramsNode.isArray()) {
                    for (JsonNode p : paramsNode) {
                        params.add(Map.of(
                            "name", p.path("name").asText(""),
                            "value", p.path("value").asText(""),
                            "type", p.path("type").asText("query"),
                            "description", p.path("description").asText("")
                        ));
                    }
                }
                result.put("params", params);
                // headers
                var headers = new java.util.ArrayList<Map<String, String>>();
                JsonNode headersNode = node.path("http").path("headers");
                if (headersNode.isArray()) {
                    for (JsonNode h : headersNode) {
                        headers.add(Map.of(
                            "name", h.path("name").asText(""),
                            "value", h.path("value").asText("")
                        ));
                    }
                }
                result.put("headers", headers);
                // body
                JsonNode bodyNode = node.path("body");
                if (!bodyNode.isMissingNode()) {
                    result.put("body", Map.of(
                        "type", bodyNode.path("type").asText("json"),
                        "data", bodyNode.path("data").toString()
                    ));
                } else {
                    result.put("body", null);
                }
                result.put("raw", raw);
                ctx.json(result);
            } catch (java.io.IOException e) {
                writeError(ctx, 500, "Failed to read Bruno request: " + e.getMessage());
            }
        });

        // ── OpenAPI spec browser ──
        // List spec files under repo/openapi/ for use in tool configs.
        app.get("/api/openapi", ctx -> {
            try {
                java.nio.file.Path openApiRoot = java.nio.file.Path.of(repoPath, "openapi");
                if (!java.nio.file.Files.isDirectory(openApiRoot)) {
                    ctx.json(Map.of("specs", List.of()));
                    return;
                }
                var specs = java.nio.file.Files.walk(openApiRoot)
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".json");
                        })
                        .map(p -> {
                            java.nio.file.Path rootNorm = openApiRoot.toAbsolutePath().normalize();
                            String rel = rootNorm.relativize(p.toAbsolutePath().normalize()).toString()
                                    .replace("\\", "/");
                            return Map.of("file", "openapi/" + rel, "name", p.getFileName().toString());
                        })
                        .sorted((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")))
                        .toList();
                ctx.json(Map.of("specs", specs));
            } catch (java.io.IOException e) {
                writeError(ctx, 500, "Failed to list OpenAPI specs: " + e.getMessage());
            }
        });

        // Read a specific OpenAPI spec file (for UI preview of operationIds)
        app.get("/api/openapi/{spec}", ctx -> {
            String specName = ctx.pathParam("spec");
            if (!specName.matches("[A-Za-z0-9._-]+")) {
                writeError(ctx, 400, "Invalid spec name");
                return;
            }
            try {
                java.nio.file.Path specFile = java.nio.file.Path.of(repoPath, "openapi", specName).normalize();
                java.nio.file.Path openApiRoot = java.nio.file.Path.of(repoPath, "openapi").toAbsolutePath().normalize();
                if (!specFile.toAbsolutePath().normalize().startsWith(openApiRoot)) {
                    writeError(ctx, 400, "Invalid spec path");
                    return;
                }
                if (!java.nio.file.Files.exists(specFile)) {
                    writeError(ctx, 404, "Spec not found: " + specName);
                    return;
                }
                String content = java.nio.file.Files.readString(specFile);
                // Parse to JSON (works for both YAML and JSON specs) and return operation list
                JsonNode spec = specName.endsWith(".json") ? jsonMapper.readTree(content)
                        : JacksonFactory.yamlMapper().readTree(content);
                // Extract operations
                var operations = new java.util.ArrayList<Map<String, Object>>();
                JsonNode paths = spec.path("paths");
                if (paths.isObject()) {
                    paths.fields().forEachRemaining(pathEntry -> {
                        pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                            String opId = methodEntry.getValue().path("operationId").asText("");
                            String summary = methodEntry.getValue().path("summary").asText("");
                            if (!opId.isBlank()) {
                                operations.add(Map.of(
                                    "operationId", opId,
                                    "method", methodEntry.getKey().toUpperCase(),
                                    "path", pathEntry.getKey(),
                                    "summary", summary
                                ));
                            }
                        });
                    });
                }
                ctx.json(Map.of("spec", specName, "operations", operations));
            } catch (java.io.IOException e) {
                writeError(ctx, 500, "Failed to read OpenAPI spec: " + e.getMessage());
            }
        });

        app.get("/api/catalog/model-providers", ctx -> {
            ConfigLoader configLoader = new ConfigLoader(repoPath);
            AppConfig config = configLoader.loadRaw();
            ctx.json(Map.of("providers", modelProviderCatalog(config)));
        });

        app.get("/api/artifacts/{type}/{name}/status", ctx -> {
            ManifestType type = ManifestType.fromPath(ctx.pathParam("type"));
            String name = ctx.pathParam("name");
            String yaml = manifestFileService.readManifest(type, name);
            ValidationResult validation = validate(type, yaml);
            String status = validation.isValid() ? "valid" : "invalid";
            ctx.json(Map.of(
                "status", status,
                "certified", false,
                "valid", validation.isValid(),
                "errors", validation.getErrors()
            ));
        });

        app.get("/api/gateway/status", ctx -> {
            ConfigLoader configLoader = new ConfigLoader(repoPath);
            AppConfig config = configLoader.loadRaw();
            SecurityConfig security = config.getSecurity();
            boolean apiKeyRequired = security != null && security.getApiKey() != null && !security.getApiKey().isBlank();

            ctx.json(Map.of(
                "mcp", Map.of(
                    "configured", mcpRequestHandler != null,
                    "streamableHttp", mcpRequestHandler != null,
                    "sse", mcpSseTransport != null,
                    "endpoint", "/mcp",
                    "sseEndpoint", "/mcp/sse"
                ),
                "openAiCompatible", Map.of(
                    "configured", openAiProxy != null,
                    "modelsEndpoint", "/v1/models",
                    "chatCompletionsEndpoint", "/v1/chat/completions",
                    "toolsEndpoint", "/v1/tools"
                ),
                "policy", Map.of(
                    "apiKeyRequired", apiKeyRequired,
                    "certifiedOnly", false,
                    "exposedArtifacts", Map.of(
                        "agents", runtimeFacade.listAgents().size(),
                        "tools", runtimeFacade.listTools().size(),
                        "pipelines", runtimeFacade.listPipelines().size(),
                        "skills", runtimeFacade.listSkills().size()
                    )
                )
            ));
        });

        // Token usage tracking endpoint
        app.get("/api/usage", ctx -> {
            var router = runtimeFacade.getModelRouter();
            if (router == null) {
                ctx.json(Map.of("providers", Map.of(), "totalTokens", 0, "ollamaAvailable", false));
                return;
            }
            var usageMap = router.getUsageByProvider();
            var providerStats = new java.util.HashMap<String, Map<String, Object>>();
            for (var entry : usageMap.entrySet()) {
                long[] tokens = entry.getValue();
                providerStats.put(entry.getKey(), Map.of(
                    "promptTokens", tokens[0],
                    "completionTokens", tokens[1],
                    "totalTokens", tokens[0] + tokens[1]
                ));
            }
            ctx.json(Map.of(
                "providers", providerStats,
                "totalTokens", router.getTotalTokens(),
                "ollamaAvailable", router.isOllamaAvailable(),
                "ollamaModels", router.getOllamaModels()
            ));
        });

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

        // Resume a paused pipeline execution with form data
        app.post("/api/pipeline/{executionId}/resume", ctx -> {
            String executionId = ctx.pathParam("executionId");
            JsonNode formData = parseInput(ctx);
            try {
                var pipelineExecutor = runtimeFacade.getPipelineExecutor();
                var result = pipelineExecutor.resume(executionId, formData);
                ctx.status(result.getStatus() == ExecutionStatus.SUCCESS ? 200 : 400);
                ctx.json(toRunPayload(result));
            } catch (IllegalStateException e) {
                log.error("Failed to resume pipeline execution '{}'", executionId, e);
                writeError(ctx, 404, e.getMessage());
            }
        });

        // Async execution endpoint — returns immediately with execution ID
        app.post("/api/run/{type}/{name}/async", ctx -> {
            String type = ctx.pathParam("type");
            String name = ctx.pathParam("name");
            JsonNode input = parseInput(ctx);
            String conversationId = ctx.queryParam("conversationId");
            String executionId = java.util.UUID.randomUUID().toString();

            switch (type) {
                case "agent" -> runtimeFacade.runAgentAsync(name, input, conversationId);
                case "pipeline" -> runtimeFacade.runPipelineAsync(name, input);
                default -> {
                    writeError(ctx, 400, "Async execution not supported for type: " + type);
                    return;
                }
            }
            ctx.json(Map.of("executionId", executionId, "status", "RUNNING"));
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

        // Execute a new task using universal.task agent
        app.post("/api/task/execute", ctx -> {
            try {
                JsonNode body = jsonMapper.readTree(ctx.body());
                String request = body.get("request").asText();
                JsonNode context = body.has("context") ? body.get("context") : null;
                String agentName = body.has("agentName") ? body.get("agentName").asText() : "universal.task";
                
                log.info("POST /api/task/execute - request: {}, agent: {}", request, agentName);
                
                // Build input JSON for natural language mode
                var inputJson = jsonMapper.createObjectNode();
                inputJson.put("request", request);
                if (context != null) {
                    inputJson.set("context", context);
                }
                
                // Execute via specified agent (defaults to universal.task) with intelligent routing
                RunResult result = runtimeFacade.runAgent(agentName, inputJson, null);
                
                // Convert to API response format
                Map<String, Object> response = new HashMap<>();
                response.put("success", result.getStatus() == ExecutionStatus.SUCCESS);
                if (result.getStatus() == ExecutionStatus.SUCCESS) {
                    response.put("result", result.getOutput());
                    response.put("executionId", result.getExecutionId());
                } else {
                    response.put("error", result.getError());
                }
                
                ctx.json(response);
                ctx.status(result.getStatus() == ExecutionStatus.SUCCESS ? 200 : 500);
                
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
                String agentName = body.has("agentName") ? body.get("agentName").asText() : "universal.task";
                
                log.info("POST /api/task/continue - conversation: {}, request: {}, agent: {}", 
                    conversationId, request, agentName);
                
                // Build input JSON for natural language mode
                var inputJson = jsonMapper.createObjectNode();
                inputJson.put("request", request);
                if (context != null) {
                    inputJson.set("context", context);
                }
                
                // Execute with conversation ID using specified agent
                RunResult result = runtimeFacade.runAgent(agentName, inputJson, conversationId);
                
                // Convert to API response format
                Map<String, Object> response = new HashMap<>();
                response.put("success", result.getStatus() == ExecutionStatus.SUCCESS);
                response.put("conversationId", conversationId);
                if (result.getStatus() == ExecutionStatus.SUCCESS) {
                    response.put("result", result.getOutput());
                    response.put("executionId", result.getExecutionId());
                } else {
                    response.put("error", result.getError());
                }
                
                ctx.json(response);
                ctx.status(result.getStatus() == ExecutionStatus.SUCCESS ? 200 : 500);
                
            } catch (Exception e) {
                log.error("Task continuation failed", e);
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });
        
        // ── Conversation history endpoints ──
        app.get("/api/conversations", ctx -> {
            var memory = runtimeFacade.getConversationMemory();
            if (memory == null) {
                ctx.json(Map.of("conversations", List.of()));
                return;
            }
            var conversations = memory.listConversations().stream()
                .map(id -> Map.of("id", id))
                .toList();
            ctx.json(Map.of("conversations", conversations));
        });

        app.get("/api/conversations/{id}/messages", ctx -> {
            var memory = runtimeFacade.getConversationMemory();
            if (memory == null) {
                ctx.json(Map.of("messages", List.of()));
                return;
            }
            String conversationId = ctx.pathParam("id");
            var messages = memory.loadHistory(conversationId);
            var serialized = messages.stream()
                .map(msg -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    return m;
                })
                .toList();
            ctx.json(Map.of("messages", serialized));
        });

        app.delete("/api/conversations/{id}", ctx -> {
            var memory = runtimeFacade.getConversationMemory();
            if (memory == null) {
                writeError(ctx, 404, "Conversation memory not configured");
                return;
            }
            String conversationId = ctx.pathParam("id");
            memory.clearConversation(conversationId);
            ctx.json(Map.of("deleted", true));
        });

        // Get task service info
        app.get("/api/task/info", ctx -> {
            Map<String, Object> info = new HashMap<>();
            info.put("available_tools", runtimeFacade.listTools().size());
            info.put("available_agents", runtimeFacade.listAgents().size());
            info.put("available_pipelines", runtimeFacade.listPipelines().size());
            info.put("available_skills", runtimeFacade.listSkills().size());
            info.put("agent", "universal.task");
            info.put("status", "ready");
            ctx.json(info);
        });

        // ── SSE streaming endpoint for chat UI ──
        // Returns execution events as Server-Sent Events for real-time chat rendering.
        app.post("/api/task/stream", ctx -> {
            try {
                JsonNode body = jsonMapper.readTree(ctx.body());
                String request = body.get("request").asText();
                JsonNode context = body.has("context") ? body.get("context") : null;
                String agentName = body.has("agentName") ? body.get("agentName").asText() : "universal.task";
                String conversationId = body.has("conversationId") ? body.get("conversationId").asText() : null;

                // Model override support
                String modelOverride = body.has("model") ? body.get("model").asText() : null;

                log.info("POST /api/task/stream - request: {}, agent: {}, model: {}", request, agentName, modelOverride);

                ctx.contentType("text/event-stream");
                ctx.header("Cache-Control", "no-cache");
                ctx.header("Connection", "keep-alive");

                // Send "started" event
                ctx.res().getWriter().write("event: started\ndata: {\"agent\":\"" + agentName + "\"}\n\n");
                ctx.res().getWriter().flush();

                // Build input
                var inputJson = jsonMapper.createObjectNode();
                inputJson.put("request", request);
                if (context != null) {
                    inputJson.set("context", context);
                }
                if (modelOverride != null) {
                    inputJson.put("model", modelOverride);
                }

                // Execute (blocking for now — agent calls are synchronous)
                RunResult result = runtimeFacade.runAgent(agentName, inputJson, conversationId);

                // Send result event
                Map<String, Object> resultEvent = new HashMap<>();
                resultEvent.put("success", result.getStatus() == ExecutionStatus.SUCCESS);
                if (result.getStatus() == ExecutionStatus.SUCCESS) {
                    resultEvent.put("result", result.getOutput());
                    resultEvent.put("executionId", result.getExecutionId());
                } else {
                    resultEvent.put("error", result.getError());
                }
                if (result.getExecutionTrace() != null) {
                    resultEvent.put("executionTrace", result.getExecutionTrace());
                }

                String eventData = jsonMapper.writeValueAsString(resultEvent);
                ctx.res().getWriter().write("event: result\ndata: " + eventData + "\n\n");
                ctx.res().getWriter().write("event: done\ndata: {}\n\n");
                ctx.res().getWriter().flush();

            } catch (Exception e) {
                log.error("Task stream failed", e);
                try {
                    String errorJson = jsonMapper.writeValueAsString(Map.of("error", e.getMessage()));
                    ctx.res().getWriter().write("event: error\ndata: " + errorJson + "\n\n");
                    ctx.res().getWriter().flush();
                } catch (Exception ex) {
                    log.error("Failed to write SSE error", ex);
                }
            }
        });

        // ── MCP (Model Context Protocol) endpoint ──
        // Streamable HTTP transport: external chat UIs (Open WebUI, etc.)
        // discover and invoke Hubbers artifacts via JSON-RPC 2.0.
        app.post("/mcp", ctx -> {
            if (mcpRequestHandler == null) {
                writeError(ctx, 501, "MCP server not configured");
                return;
            }
            try {
                McpRequest mcpReq = jsonMapper.readValue(ctx.body(), McpRequest.class);
                var response = mcpRequestHandler.handle(mcpReq);
                if (response.isPresent()) {
                    ctx.contentType("application/json").result(jsonMapper.writeValueAsString(response.get()));
                } else {
                    // Notifications return 202 with no body
                    ctx.status(202).result("");
                }
            } catch (Exception e) {
                log.error("MCP request failed", e);
                McpResponse errorResp = McpResponse.internalError(null, e.getMessage());
                ctx.contentType("application/json").result(jsonMapper.writeValueAsString(errorResp));
            }
        });

        // ── MCP SSE transport (backward compatibility) ──
        // Older MCP clients connect via SSE: GET /mcp/sse to get endpoint URL,
        // then POST /mcp/messages?sessionId=... to send JSON-RPC.
        app.get("/mcp/sse", ctx -> {
            if (mcpSseTransport == null && mcpRequestHandler != null) {
                mcpSseTransport = new McpSseTransport(mcpRequestHandler, jsonMapper);
            }
            if (mcpSseTransport == null) {
                writeError(ctx, 501, "MCP server not configured");
                return;
            }
            mcpSseTransport.handleSseConnect(ctx);
        });

        app.post("/mcp/messages", ctx -> {
            if (mcpSseTransport == null) {
                writeError(ctx, 501, "MCP SSE transport not initialized");
                return;
            }
            mcpSseTransport.handleMessage(ctx);
        });

        // ── OpenAI-compatible proxy ──
        // Allows any OpenAI-compatible client (Open WebUI, llama.cpp, LM Studio,
        // text-generation-webui, Chatbox) to discover and invoke Hubbers artifacts
        // via standard function calling.
        app.get("/v1/models", ctx -> {
            if (openAiProxy == null) {
                writeError(ctx, 501, "OpenAI proxy not configured");
                return;
            }
            openAiProxy.handleListModels(ctx);
        });

        app.post("/v1/chat/completions", ctx -> {
            if (openAiProxy == null) {
                writeError(ctx, 501, "OpenAI proxy not configured");
                return;
            }
            openAiProxy.handleChatCompletions(ctx);
        });

        app.get("/v1/tools", ctx -> {
            if (openAiProxy == null) {
                writeError(ctx, 501, "OpenAI proxy not configured");
                return;
            }
            ctx.contentType("application/json").result(
                jsonMapper.writeValueAsString(openAiProxy.getToolDefinitions()));
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
                case SKILL -> manifestValidator.validateSkill(yamlMapper.readValue(yaml, SkillManifest.class));
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
            case SKILL -> runtimeFacade.runSkill(name, input);
        };
    }

    private List<Map<String, Object>> toolDriverCatalog() {
        return List.of(
                driver("http", "HTTP Request", "HTTP API calls using manifest config", "network"),
                driver("http.webhook", "HTTP Webhook", "Start a local webhook listener", "network"),
                driver("csv.read", "CSV Read", "Read rows from a CSV file", "filesystem"),
                driver("csv.write", "CSV Write", "Write rows to a CSV file", "filesystem"),
                driver("rss", "RSS Fetch", "Fetch and parse RSS or Atom feeds", "network"),
                driver("firecrawl", "Firecrawl", "Scrape web pages through Firecrawl", "network"),
                driver("file.ops", "File Operations", "Read, write, list, copy, move, or delete files", "high-risk"),
                driver("shell.exec", "Shell Execute", "Execute shell commands", "high-risk"),
                driver("process.manage", "Process Manage", "Inspect and control local processes", "high-risk"),
                driver("docker", "Docker", "Run Docker-based workloads", "high-risk"),
                driver("sql.query", "SQL Query", "Run SQL queries against configured databases", "data"),
                driver("user-interaction", "User Interaction", "Request human input during execution", "interactive"),
                driver("browser.pinchtab", "Pinchtab Browser", "Interact with browser automation", "network"),
                driver("lucene.kv", "Lucene Key Value", "Read and write local Lucene key-value data", "storage"),
                driver("vector.lucene.enrich", "Lucene Vector Context", "Enrich prompts from vector search", "storage"),
                driver("vector.lucene.upsert", "Lucene Vector Upsert", "Store vector records locally", "storage"),
                driver("vector.lucene.search", "Lucene Vector Search", "Search local vector records", "storage"),
                driver("bruno", "Bruno Collection", "Execute a request from a Bruno API collection", "network"),
                driver("openapi", "OpenAPI Operation", "Execute an operation from an OpenAPI specification", "network")
        );
    }

    private Map<String, Object> driver(String type, String label, String description, String risk) {
        return Map.of(
                "type", type,
                "label", label,
                "description", description,
                "risk", risk
        );
    }

    private List<Map<String, Object>> modelProviderCatalog(AppConfig config) {
        return List.of(
                provider("ollama", "Ollama", true, config.getOllama() != null, config.getOllama() != null ? config.getOllama().getDefaultModel() : null),
                provider("llama-cpp", "llama.cpp", true, config.getLlamaCpp() != null, config.getLlamaCpp() != null ? config.getLlamaCpp().getDefaultModel() : null),
                provider("openai", "OpenAI", false, config.getOpenai() != null && config.getOpenai().getApiKey() != null && !config.getOpenai().getApiKey().isBlank(), config.getOpenai() != null ? config.getOpenai().getDefaultModel() : null),
                provider("azure-openai", "Azure OpenAI", false, config.getAzureOpenai() != null && config.getAzureOpenai().getApiKey() != null && !config.getAzureOpenai().getApiKey().isBlank(), config.getAzureOpenai() != null ? config.getAzureOpenai().getDefaultModel() : null),
                provider("anthropic", "Anthropic", false, config.getAnthropic() != null && config.getAnthropic().getApiKey() != null && !config.getAnthropic().getApiKey().isBlank(), config.getAnthropic() != null ? config.getAnthropic().getDefaultModel() : null)
        );
    }

    private Map<String, Object> provider(String id, String label, boolean local, boolean configured, String defaultModel) {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", id);
        provider.put("label", label);
        provider.put("local", local);
        provider.put("configured", configured);
        provider.put("defaultModel", defaultModel == null ? "" : defaultModel);
        return provider;
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
                case SKILL -> null; // Skills don't support forms
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
        if (result.getExecutionTrace() != null) {
            payload.put("executionTrace", result.getExecutionTrace());
        }
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
