# Software Architecture Analysis - Hubbers Runtime

**Date**: March 30, 2026  
**Version**: 0.1.0-SNAPSHOT  
**Architect**: AI Software Architect Review

---

## Executive Summary

Hubbers Runtime è un **framework Git-native per l'orchestrazione di AI agents, tools e pipelines** definiti come artifact YAML versionabili. L'architettura è basata su **interfaces pulite, pattern plugin, e composizione dichiarativa**.

**Assessment**: ✅ Strong foundation, production-ready with minor improvements needed.

---

## Technology Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Runtime** | Java | 21 | Language & native HttpClient |
| **Build** | Maven + Shade | 3.x | Fat JAR & GraalVM native compilation |
| **CLI** | Picocli | 4.7.6 | Command parsing & subcommands |
| **Web** | Javalin (Jetty 11) | 5.6.3 | REST API server |
| **Config** | Jackson (JSON/YAML) | 2.17.2 | Serialization & manifest parsing |
| **Search** | Apache Lucene | 9.11.1 | Vector search (KNN 256-D) + NoSQL KV store |
| **Logging** | SLF4J + Logback | 2.0.16 / 1.5.8 | Structured logging |
| **Testing** | JUnit Jupiter | 5.11.3 | Unit testing |

**Native Compilation**: GraalVM support con reflection-config per standalone binary (~15MB).

---

## Project Structure

### Core Packages

| Package | Purpose | Key Classes |
|---------|---------|------------|
| [**app**](src/main/java/org/hubbers/app) | Bootstrap & runtime | Bootstrap.java, RuntimeFacade.java |
| [**agent**](src/main/java/org/hubbers/agent) | LLM orchestration | AgentExecutor.java, AgentPromptBuilder.java |
| [**tool**](src/main/java/org/hubbers/tool) | Pluggable tools (10 drivers) | ToolDriver.java, ToolExecutor.java |
| [**pipeline**](src/main/java/org/hubbers/pipeline) | Workflow orchestration | PipelineExecutor.java, PipelineState.java |
| [**model**](src/main/java/org/hubbers/model) | LLM backends | ModelProvider.java, ModelProviderRegistry.java |
| [**artifact**](src/main/java/org/hubbers/artifact) | Manifest discovery | ArtifactRepository.java, LocalArtifactRepository.java |
| [**manifest**](src/main/java/org/hubbers/manifest) | YAML data classes | AgentManifest, ToolManifest, PipelineManifest |
| [**validation**](src/main/java/org/hubbers/validation) | Schema validation | SchemaValidator.java, ManifestValidator.java |
| [**config**](src/main/java/org/hubbers/config) | Configuration | ConfigLoader.java, AppConfig |
| [**cli**](src/main/java/org/hubbers/cli) | CLI commands | HubbersCommand.java |
| [**web**](src/main/java/org/hubbers/web) | Web API | WebServer.java, ManifestFileService |
| [**execution**](src/main/java/org/hubbers/execution) | Result abstractions | RunResult.java, ExecutionStatus |
| [**util**](src/main/java/org/hubbers/util) | Utilities | JacksonFactory.java |

### Artifact Organization

```
repo/
├── agents/          4 agents (rss.sentiment, rss.ner, rss.translate, text.summarizer)
├── tools/           10 tools (HTTP, Docker, RSS, CSV RW, Lucene vector×3, Lucene KV, Pinchtab)
└── pipelines/       7 pipelines (rss.csv, rss.sentiment.csv, etc.)
```

---

## Core Abstractions

### 1. ToolDriver Interface

```java
public interface ToolDriver {
    String type();                                    // Tool type identifier
    JsonNode execute(ToolManifest manifest, JsonNode input);
}
```

**Implementations** (10 total):

| Category | Drivers |
|----------|---------|
| **Generic** | HttpToolDriver, DockerToolDriver |
| **Content** | RssToolDriver |
| **Data I/O** | CsvReadToolDriver, CsvWriteToolDriver |
| **Search** | LuceneVectorSearchToolDriver, LuceneVectorUpsertToolDriver, LuceneVectorContextToolDriver, LuceneKvToolDriver |
| **Automation** | PinchtabBrowserToolDriver |

### 2. ModelProvider Interface

```java
public interface ModelProvider {
    String providerName();
    ModelResponse generate(ModelRequest request);
}
```

**Implementations**: OpenAiModelProvider, OllamaModelProvider

### 3. ArtifactRepository Interface

```java
public interface ArtifactRepository {
    List<String> listAgents/Tools/Pipelines();
    AgentManifest loadAgent(String name);
    ToolManifest loadTool(String name);
    PipelineManifest loadPipeline(String name);
}
```

**Implementation**: LocalArtifactRepository (file-based YAML loading)

### 4. Execution Result Wrapper

[RunResult.java](src/main/java/org/hubbers/execution/RunResult.java) wraps all execution results with metadata (status, output, error, timing).

---

## Design Patterns

| Pattern | Location | Purpose |
|---------|----------|---------|
| **Registry** | ToolExecutor, ModelProviderRegistry | Map-based lookup: type → implementation |
| **Factory** | Bootstrap.java | Component creation & dependency injection |
| **Facade** | RuntimeFacade.java | Unified API for CLI/web (`runAgent`, `runTool`, `runPipeline`) |
| **Strategy** | ToolDriver, ModelProvider | Pluggable execution strategies |
| **Template Method** | AgentExecutor, ToolExecutor, PipelineExecutor | Fixed flow: validate → execute → validate → metadata |
| **Repository** | ArtifactRepository | Abstract artifact storage (currently file-system) |

### Config Resolution Pattern

**Used consistently across all tool drivers:**

```
Priority (high → low):
1. Runtime input parameter (JSON field)
2. Manifest config section (YAML)
3. Hardcoded default
```

**Example** (from any tool driver):
```java
private String resolveIndexPath(ToolManifest manifest, JsonNode input) {
    // 1. Check input JSON
    JsonNode inputPath = input.get("index_path");
    if (inputPath != null && inputPath.isTextual()) {
        return inputPath.asText();
    }
    
    // 2. Check manifest YAML config
    Object configured = manifest.getConfig().get("index_path");
    if (configured != null) {
        return configured.toString();
    }
    
    // 3. Use default
    return DEFAULT_INDEX_PATH;
}
```

---

## Architectural Pattern: Plugin Architecture

```
┌─────────────────────────────────────────────────┐
│           RuntimeFacade (API Facade)            │
├─────────────────┬───────────────┬───────────────┤
│  AgentExecutor  │ ToolExecutor  │PipelineExecutor│
├─────────────────┴───────────────┴───────────────┤
│         Validators (Schema + Manifest)          │
├──────────────────────────────────────────────────┤
│    Registries:                                  │
│    • ModelProviderRegistry                      │
│    • ToolExecutor (map: type → implementation)  │
├──────────────────────────────────────────────────┤
│ Plugins:                                        │
│  • ToolDriver implementations (10)              │
│  • ModelProvider implementations (2)            │
├──────────────────────────────────────────────────┤
│ Artifact Repository (YAML discovery & loading)  │
└──────────────────────────────────────────────────┘
```

### Declarative Composition (Pipelines)

Pipelines are **DAGs of steps** defined in YAML:

```yaml
pipeline:
  name: rss.sentiment.csv
  
steps:
  - id: fetch
    tool: rss.fetch
  
  - id: sentiment
    agent: rss.sentiment
    input_mapping:
      items: ${steps.fetch.output.items}  # Template substitution
  
  - id: write_csv
    tool: csv.write
    input_mapping:
      items: ${steps.sentiment.output.items}
      file_path: ${csv_output_path}
```

**Flow**: Sequential execution with fail-fast semantics. Template engine resolves `${steps.id.output.field}` references.

---

## Module Architecture

### Agent Module

**Files**: AgentExecutor, AgentPromptBuilder, AgentRunContext  
**Flow**: Manifest → Input validation → Prompt building → LLM call → Output validation → Metadata

### Tool Module (10 Drivers)

**Categories**:

1. **Generic**: HttpToolDriver (REST APIs), DockerToolDriver (containerized tools)
2. **Content**: RssToolDriver (RSS/Atom feed parsing)
3. **File I/O**: CsvReadToolDriver, CsvWriteToolDriver
4. **Vector Search** (Lucene 9.11.1):
   - LuceneVectorSearchToolDriver (KNN 256-D search)
   - LuceneVectorUpsertToolDriver (indexing)
   - LuceneVectorContextToolDriver (context enrichment)
   - LuceneKvToolDriver (key-value store: get/put/delete/batch_put/list_keys)
5. **Browser Automation**: PinchtabBrowserToolDriver (headless/headed Chrome via HTTP)

### Pipeline Module

- **Sequential execution** of tool/agent steps
- **Input mapping** with template syntax: `${steps.step-id.output.field}`
- **PipelineState** passes data between steps
- **Fail-fast**: errors stop pipeline immediately

### Validation Module

- **SchemaValidator**: Type checking (string, number, boolean, object, array)
- **ManifestValidator**: YAML structure validation
- **Applied at**: agent input/output, tool input/output, manifest load

### CLI Module

PicoCLI command hierarchy:

```
hubbers
├── list {agents|tools|pipelines}
├── agent run <name> --input <json|file>
├── tool run <name> --input <json|file>
├── pipeline run <name> --input <json|file>
└── web [--port <port>]
```

### Web Module

- Javalin-based REST API
- HTTP endpoints for running agents/tools/pipelines
- File upload/download for manifests

---

## Manifest Structures

### Agent Manifest

```yaml
agent:
  name: rss.sentiment
  version: 1.0.0
  description: Sentiment analysis for RSS items

model:
  provider: ollama                  # or "openai"
  name: llama3.2:3b
  temperature: 0.1

instructions:
  system_prompt: "You are a sentiment analyst..."

input:
  schema:
    type: object
    properties:
      items: { type: array, required: true }

output:
  schema:
    type: object
    properties:
      items: { type: array, required: true }

tools: []                           # List of tool names agent can use
examples: [...]
```

### Tool Manifest

```yaml
tool:
  name: csv.read
  version: 1.0.0

type: csv.read                      # Maps to ToolDriver.type()

config:
  default_file_path: ./datasets/data.csv

input:
  schema:
    type: object
    properties:
      file_path: { type: string, required: false }

output:
  schema:
    type: object
    properties:
      items: { type: array, required: true }

examples: [...]
```

### Pipeline Manifest

```yaml
pipeline:
  name: rss.csv
  version: 1.0.0

steps:
  - id: fetch
    tool: rss.fetch
  
  - id: write_csv
    tool: csv.write
    input_mapping:
      items: ${steps.fetch.output.items}  # Template substitution

examples: [...]
```

---

## Key Infrastructure Components

| Component | File | Purpose |
|-----------|------|---------|
| **Config Loader** | config/ConfigLoader.java | Reads `application.yaml` (repo_root, OpenAI, Ollama config) |
| **JSON Factory** | util/JacksonFactory.java | JSON/YAML mapper creation |
| **Vector Support** | tool/LuceneVectorSupport.java | Hash-based 256-D embedding, document creation |
| **Artifact Scanner** | artifact/ArtifactScanner.java | Directory traversal for YAML discovery |

---

## Execution Flow Example

**Pipeline**: rss.sentiment.csv

```
Input: {feeds: ["https://example.com/rss.xml"], limit: 5}
  ↓
Step 1 (rss.fetch):
  - Tool: RssToolDriver
  - Output: {items: [5 articles with title, link, summary, content]}
  ↓
Step 2 (rss.sentiment):
  - Agent: LLM-based sentiment analyzer
  - Input mapping: items = ${steps.fetch.output.items}
  - Output: {items: [5 articles + sentiment field]}
  ↓
Step 3 (csv.write):
  - Tool: CsvWriteToolDriver
  - Input mapping: items = ${steps.sentiment.output.items}
  - Output: {file_path: ./rss-sentiment.csv, rows: 5}
  ↓
Return final output
```

---

## Strengths

### 1. Separation of Concerns ✅
- Clean package boundaries (agent, tool, pipeline, validation, config)
- Interface-driven design facilitates testing

### 2. Git-Native Artifacts ✅
- Agents, tools, pipelines **versioned as YAML** in `repo/`
- Team collaboration via Git workflows
- No database required

### 3. Extensibility ✅
- Add tool = implement `ToolDriver` + register in Bootstrap
- Add LLM backend = implement `ModelProvider` + register
- **Proven**: Lucene KV and Pinchtab Browser added without core changes

### 4. Validation Layer ✅
- Schema validation (input/output type checking)
- Manifest validation (structure + examples presence)
- Fail-fast approach

### 5. Config Flexibility ✅
- 3-tier resolution (input → manifest → default)
- Environment variable support (`${OPENAI_API_KEY}`)
- Profile support (e.g., Pinchtab: headless/headed)

### 6. Native Compilation Ready ✅
- GraalVM reflect-config.json prepared
- Fat JAR via Maven Shade
- Standalone binary ~15MB

---

## Concerns & Technical Debt

### 1. ⚠️ Validation Hardcoding (HIGH PRIORITY)

**File**: [ManifestValidator.java](src/main/java/org/hubbers/validation/ManifestValidator.java) lines 28-43

**Issue**: 
```java
if (!"http".equals(manifest.getType())
    && !"docker".equals(manifest.getType())
    && !"rss".equals(manifest.getType())
    // ... 10 hardcoded checks
```

Every new tool type requires code change in validator.

**Impact**: 
- Forgot to update → build passes, runtime fails with "Invalid tool type"
- Violates Open/Closed Principle

**Recommendation**:
```java
// Dynamic type lookup from registry
Set<String> validTypes = toolExecutor.getRegisteredTypes();
if (!validTypes.contains(manifest.getType())) {
    result.addError("Invalid tool type: " + manifest.getType()
        + ". Valid: " + validTypes);
}
```

**Priority**: HIGH  
**Complexity**: LOW  
**Estimated Effort**: 2 hours

---

### 2. ⚠️ Error Handling Inconsistency (MEDIUM PRIORITY)

**Issue**: Tool drivers use mix of exceptions:
- `IllegalStateException` (LuceneKv, Pinchtab)
- `IllegalArgumentException` (missing parameters)
- Some wrap IOException, some don't

**Recommendation**: Define custom exception hierarchy
```java
public class ToolExecutionException extends Exception {
    private final ErrorCode code;
    private final String toolType;
    
    public enum ErrorCode {
        CONFIG_MISSING,
        INVALID_INPUT,
        EXTERNAL_SERVICE_UNAVAILABLE,
        EXECUTION_TIMEOUT
    }
}
```

**Priority**: MEDIUM  
**Complexity**: MEDIUM  
**Estimated Effort**: 1 week

---

### 3. ⚠️ Pinchtab Session Management (HIGH PRIORITY)

**File**: [PinchtabBrowserToolDriver.java](src/main/java/org/hubbers/tool/PinchtabBrowserToolDriver.java)

**Issue**: 
- Sessions stored in-memory `Map<String, SessionState>`
- No automatic cleanup (only manual `close` action)
- Memory leak in long-running web server mode

**Recommendation**: TTL-based cleanup
```java
private static final int MAX_IDLE_MINUTES = 30;
private final Map<String, TimedSession> sessions = new ConcurrentHashMap<>();

// Scheduled cleanup every 5 minutes
private void scheduleCleanup() {
    ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1);
    cleaner.scheduleAtFixedRate(() -> {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> 
            now - entry.getValue().lastAccessTime > MAX_IDLE_MINUTES * 60_000
        );
    }, 5, 5, TimeUnit.MINUTES);
}
```

**Priority**: HIGH  
**Complexity**: MEDIUM  
**Estimated Effort**: 1 day

---

### 4. ℹ️ Lucene Index Path Management (LOW PRIORITY)

**Issue**: Hardcoded default paths with collision risk:
- Vector: `./datasets/lucene/rss-sentiment-index`
- KV: `./datasets/lucene/kv-store`

**Recommendation**: Central index registry
```java
public class LuceneIndexRegistry {
    private final Path registryFile;
    private final Map<String, IndexMetadata> indexes;
    
    public FSDirectory getOrCreate(String name, IndexSchema schema) {
        // Resolve conflicts, track versions, cleanup unused
    }
}
```

**Priority**: LOW  
**Complexity**: MEDIUM  
**Estimated Effort**: 1 week

---

### 5. ℹ️ Pipeline Error Recovery (LOW PRIORITY)

**File**: [PipelineExecutor.java](src/main/java/org/hubbers/pipeline/PipelineExecutor.java)

**Issue**: Fail-fast design → no retry, no partial success, no rollback.

**Recommendation** (future enhancement):
- Retry policy per step (for idempotent operations)
- Compensation steps (rollback pattern)
- Checkpoint/resume for long-running pipelines

**Priority**: LOW (future)  
**Complexity**: HIGH  
**Estimated Effort**: 3-4 weeks

---

### 6. ℹ️ Schema Validator Limitations (LOW PRIORITY)

**File**: [SchemaValidator.java](src/main/java/org/hubbers/validation/SchemaValidator.java)

**Current Support**: `string, number, boolean, object, array`

**Missing**:
- String patterns (regex, format: email/url)
- Number constraints (min/max, range)
- Array constraints (minItems, uniqueItems)
- Enum validation

**Recommendation**: Integrate JSON Schema library (`everit-org/json-schema`) or extend gradually.

**Priority**: LOW  
**Complexity**: MEDIUM  
**Estimated Effort**: 2 weeks

---

## Architecture Decision Records (ADRs)

### ADR-001: Lucene for Vector Search

**Status**: Accepted  
**Date**: 2026-03-30

**Context**: Need semantic search for RSS items with context enrichment.

**Decision**: Use Apache Lucene 9.11.1 for vector search instead of dedicated vector DB (Pinecone, Weaviate, Milvus).

**Rationale**:
- No external service dependency (embedded)
- Native Java integration
- GraalVM-compatible
- Sufficient for MVP scale (<100k documents)
- 256-D hash-based embedding (simple, deterministic)

**Consequences**:
- ✅ Zero-setup, portable, fast for small datasets
- ❌ Limited scalability vs specialized vector DBs
- ❌ No clustering/replication built-in

**Alternatives Considered**:
- Pinecone: Cloud service, cost, operational dependency
- Weaviate: Requires Docker, complex setup
- FAISS: C++ binding, GraalVM complications

**Future**: Revisit if >1M documents or distributed requirements emerge. Consider ANN library upgrade (HNSW).

---

### ADR-002: Git-Native Artifact Storage

**Status**: Accepted  
**Date**: 2026-03-30

**Context**: Need versioned, collaborative definition of agents/tools/pipelines.

**Decision**: Store definitions as YAML files in `repo/` directory, versioned via Git.

**Rationale**:
- Version control for AI system evolution
- Team collaboration via Git workflows (PR reviews, branches)
- No database setup required
- Portable across environments
- Human-readable, diff-able

**Consequences**:
- ✅ Simple, auditable, Git-native
- ❌ No runtime updates (requires restart)
- ❌ No centralized catalog for multi-repo scenarios

**Alternatives Considered**:
- Database storage (PostgreSQL): Requires operational setup, not Git-friendly
- REST API catalog: Centralized but adds network dependency
- Git submodules: Complex management

**Future**: Consider `ArtifactRepository` abstractions for enterprise scenarios (DB backend, remote catalog).

---

### ADR-003: Lucene as NoSQL Key-Value Store

**Status**: Accepted  
**Date**: 2026-03-30

**Context**: Need simple key-value storage for configuration data, session storage, small datasets.

**Decision**: Use Lucene for KV storage (`LuceneKvToolDriver`) instead of introducing Redis/MongoDB.

**Rationale**:
- Consistency with existing Lucene dependency (no new infra)
- Document-oriented (stores JSON values natively)
- Fast local access for MVP
- Embeddable

**Consequences**:
- ✅ Zero new dependencies, simple deployment
- ❌ Not a true KV store (no TTL, no atomic operations, no pub/sub)
- ❌ Query capabilities overkill for simple get/put

**Alternatives Considered**:
- **Redis**: Requires server, adds operational complexity, network latency
- **MongoDB**: Heavy dependency for simple KV needs
- **Embedded H2**: SQL overhead, not document-friendly
- **MapDB**: Less mature, smaller community

**Future**: If distributed cache or pub/sub needed, introduce Redis as optional backend with same `ToolDriver` interface.

---

### ADR-004: Pinchtab for Browser Automation

**Status**: Accepted  
**Date**: 2026-03-30

**Context**: Need browser automation for web scraping, testing, UI interaction (headless and visible).

**Decision**: Integrate Pinchtab HTTP API instead of Selenium/Puppeteer/Playwright.

**Rationale**:
- HTTP-based (no JVM-native library needed, loose coupling)
- Chrome DevTools Protocol underneath (industry standard)
- Token-efficient (~800 tokens/page vs 10k+ for screenshots)
- Multi-instance support (headless + headed profiles)
- Local-first security model (daemon on localhost:9867)
- Profile persistence (cookies, session, login state)

**Consequences**:
- ✅ No browser driver installation/management
- ✅ Clean separation (external service)
- ✅ Daemon mode for session reusability
- ❌ External dependency (must install Pinchtab separately)
- ❌ Network latency (HTTP overhead vs native)
- ❌ Limited to Chrome/Chromium browsers

**Alternatives Considered**:
- **Selenium WebDriver**: Heavy JVM integration, fragile CSS selectors, complex setup
- **Playwright Java**: Better than Selenium but still tight coupling, browser binaries in repo
- **Puppeteer**: Node.js only, requires separate process communication

**Future**: If Pinchtab unavailable or enterprise requires different browser, provide fallback `ToolDriver` implementation using Playwright Java.

---

## Recommendations

### Immediate Actions (Next Sprint)

| Priority | Task | Effort | Impact |
|----------|------|--------|--------|
| 🔴 HIGH | Refactor ManifestValidator (dynamic type lookup) | 2 hours | Prevent runtime failures |
| 🔴 HIGH | Add Pinchtab session TTL cleanup | 1 day | Fix memory leak |
| 🟡 MEDIUM | Standardize error handling (ToolExecutionException) | 1 week | Better diagnostics |
| 🟢 LOW | Comprehensive integration tests for new tools | 3 days | Quality assurance |

### Medium-Term Improvements (Next Quarter)

- **Lucene Index Registry**: Centralize index management, prevent collisions
- **Enhanced Schema Validation**: Integrate JSON Schema library for richer constraints
- **Pipeline Observability**: Metrics (Micrometer), structured logging (MDC), traces

### Long-Term Enhancements (Next 6 Months)

- **Artifact Repository Abstractions**: Support GitHub, S3, database backends
- **Pipeline DAG Optimization**: Parallel execution for independent steps
- **Tool Development SDK**: Maven archetype for new tool creation
- **Retry/Rollback**: Error recovery mechanisms for pipelines

---

## Security Considerations

### Current State

✅ **Strengths**:
- No hardcoded credentials (env vars: `${OPENAI_API_KEY}`)
- Pinchtab local-first (localhost:9867, IDPI restrictions)
- Input validation via schemas

⚠️ **Areas for Improvement**:
- API key injection visible in command-line history (use config files)
- Web server auth missing (add basic auth or API tokens)
- Docker tool execution: no resource limits (CPU/memory caps needed)

### Recommendations

1. **Secrets Management**: Support external secret stores (HashiCorp Vault, AWS Secrets Manager)
2. **Web API Security**: 
   - Add authentication (JWT tokens, API keys)
   - Rate limiting per client
   - CORS configuration
3. **Docker Tool Sandboxing**: 
   - Resource limits (`--cpu`, `--memory`)
   - Network isolation (`--network none` for untrusted tools)
4. **Input Sanitization**: Validate file paths, prevent directory traversal

---

## Performance Characteristics

### Current Bottlenecks

1. **LLM Latency**: OpenAI/Ollama calls dominate execution time (2-10s per agent)
2. **Sequential Pipelines**: No parallelization (blocking step-by-step)
3. **Lucene Index Warm-up**: First search slower due to cold start

### Optimization Opportunities

1. **Async Agent Execution**: Non-blocking LLM calls with futures
2. **Pipeline Parallelization**: Execute independent steps concurrently
3. **Index Caching**: Keep Lucene readers warm in memory

### Scalability Targets

| Metric | Current | Target (6 months) |
|--------|---------|-------------------|
| Agents | 4 | 50+ |
| Tools | 10 | 30+ |
| Pipelines | 7 | 100+ |
| Concurrent Users (Web) | 1-5 | 50+ |
| Vector Index Size | <10k docs | 1M docs |

---

## Testing Strategy

### Current Coverage

- ✅ Unit tests: Agent, Tool, Validation modules
- ✅ Integration test example: MainTest, LuceneKvToolDriverTest
- ❌ Missing: End-to-end pipeline tests
- ❌ Missing: Performance/load tests

### Recommended Additions

1. **Integration Tests** per tool type:
   - RssToolDriver → mock HTTP server
   - PinchtabBrowserToolDriver → Docker Pinchtab instance
   - LuceneKvToolDriver → temp directories

2. **Contract Tests** for manifest schemas (JSON Schema validation)

3. **Performance Tests**:
   - Pipeline execution benchmarks
   - Lucene KNN search latency (100k docs)
   - Concurrent tool execution

---

## Deployment Considerations

### Current Deployment Options

1. **JAR Distribution** (requires Java 21+):
   ```bash
   java -jar target/hubbers-0.1.0-SNAPSHOT.jar tool run ...
   ```

2. **Native Binary** (GraalVM):
   ```bash
   ./target/hubbers tool run ...
   ```

3. **Web Server Mode**:
   ```bash
   hubbers web --port 7070
   ```

### Production Deployment Recommendations

1. **Docker Image**:
   ```dockerfile
   FROM eclipse-temurin:21-jre-alpine
   COPY target/hubbers-0.1.0-SNAPSHOT.jar /app/hubbers.jar
   COPY repo/ /app/repo/
   ENTRYPOINT ["java", "-jar", "/app/hubbers.jar"]
   ```

2. **Kubernetes Deployment**:
   - StatefulSet for web server (persistent Lucene indexes)
   - ConfigMap for `application.yaml`
   - Secret for API keys
   - Service for web API

3. **Configuration Management**:
   - Externalize `repo/` via volume mount or Git sync sidecar
   - Environment-specific `application.yaml` (dev/staging/prod)

---

## Maintenance & Evolution

### Adding a New Tool

**Steps**:
1. Create `ToolDriver` implementation in `org.hubbers.tool`
2. Register in [Bootstrap.java](src/main/java/org/hubbers/app/Bootstrap.java) `ToolExecutor` list
3. Update [ManifestValidator.java](src/main/java/org/hubbers/validation/ManifestValidator.java) (❌ **temporary until ADR recommendation applied**)
4. Create `tool.yaml` manifest in `repo/tools/<name>/`
5. Write unit tests in `src/test/java/org/hubbers/tool/`

**Example**: See LuceneKvToolDriver, PinchtabBrowserToolDriver recently added.

### Adding a New LLM Provider

**Steps**:
1. Implement `ModelProvider` interface
2. Register in [Bootstrap.java](src/main/java/org/hubbers/app/Bootstrap.java) `ModelProviderRegistry`
3. Add config class in `org.hubbers.config`
4. Update `application.yaml` schema

**Example**: See OpenAiModelProvider, OllamaModelProvider.

---

## Glossary

| Term | Definition |
|------|------------|
| **Agent** | LLM-powered task executor with system prompt and I/O schemas |
| **Tool** | Pluggable capability (HTTP, Docker, RSS, Lucene, browser) |
| **Pipeline** | DAG workflow composing agents and tools sequentially |
| **Manifest** | YAML definition file (agent.yaml, tool.yaml, pipeline.yaml) |
| **Artifact** | Generic term for agent/tool/pipeline definition |
| **Driver** | Implementation of ToolDriver or ModelProvider interface |
| **Executor** | Component that runs agents/tools/pipelines (with validation) |
| **Registry** | Map of type strings to implementations |

---

## References

- **Codebase**: `src/main/java/org/hubbers/`
- **Manifests**: `repo/agents/`, `repo/tools/`, `repo/pipelines/`
- **Configuration**: `src/main/resources/application.yaml`
- **Documentation**: [README.md](README.md)
- **Build**: [pom.xml](pom.xml)

---

## Revision History

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-03-30 | 1.0 | AI Software Architect | Initial comprehensive architecture analysis |

---

**Next Review**: After implementing Phase 1 recommendations (ManifestValidator refactor, session cleanup, error handling standardization).
