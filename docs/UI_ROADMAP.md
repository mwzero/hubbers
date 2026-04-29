---
title: UI Roadmap
---

# UI Roadmap

This roadmap turns the Hubbers UI into a local corporate agent studio for designing, validating, certifying, and operating agents, tools, skills, and pipelines.

The target experience is not a marketing dashboard. It should feel like an engineering and governance workbench: dense, inspectable, fast, and explicit about risk.

## Product Direction

Hubbers should support three primary UI modes:

1. **Design Studio**: create and edit agents, tools, skills, and pipelines with visual builders that stay aligned with backend schemas.
2. **Governed Runtime**: run artifacts locally with clear policy status, approvals, execution traces, and redacted logs.
3. **External Chat Gateway**: configure how certified artifacts are exposed to local or corporate chat clients through MCP and OpenAI-compatible endpoints.

Hubbers should also treat local knowledge stores as first-class runtime assets. The existing Lucene vector tool drivers are a good foundation, but the UI should make indexes, documents, retrieval quality, and retention visible without requiring users to run tool manifests manually.

## Current Baseline

The UI already has useful foundations:

- Workspace shell for browsing artifacts.
- YAML and Markdown editing.
- Visual builders for agents, tools, and skills.
- Pipeline designer.
- Runner panel and execution history.
- Chat interface with artifact catalog and conversation history.
- Settings and usage pages.
- Built-in Lucene-backed storage tools: `lucene.kv`, `vector.lucene.upsert`, `vector.lucene.search`, and `vector.lucene.enrich`.

The next step is to organize these pieces around governance and safe design workflows.

## Design Principles

- **Local-first clarity**: make local model, local runtime, and local data boundaries visible.
- **Policy before execution**: show whether an artifact can run before the user presses Run.
- **Certified over available**: distinguish certified artifacts from draft or experimental artifacts everywhere.
- **Traceability by default**: every run should expose inputs, outputs, model calls, tool calls, policy decisions, and approval events.
- **Knowledge stores are governed assets**: vector indexes should have owners, schemas, source lineage, retention rules, and certification status like agents and tools.
- **No YAML drift**: visual builders must use backend-provided driver, provider, schema, and permission metadata.
- **Secret-safe UI**: secrets are write-only or status-only, never displayed as plain configuration.

## Roadmap Phases

### Phase 1: Stabilize The Existing Studio

Goal: make the current workspace reliable and aligned with runtime contracts.

Deliverables:

- Replace hardcoded tool driver options with backend-discovered driver metadata.
- Normalize model provider IDs in builders and chat model selection. Use `llama-cpp` consistently.
- Add a shared artifact status badge model: `draft`, `valid`, `invalid`, `certified`, `deprecated`.
- Add inline validation summaries to builder panels, not only toast notifications.
- Add dirty-state tracking for YAML/builder edits.
- Add save/run guards when a manifest is invalid or unsynced.
- Improve mobile and narrow-width behavior for chat sidebars and workspace panels.

First implementation slice:

- Add `/api/catalog/drivers` or equivalent backend metadata endpoint.
- Update `ToolWizard` to consume driver metadata instead of static `DRIVER_TYPES`.
- Update agent and skill model provider selects to use backend provider IDs.
- Add status badges to artifact rows in the sidebar.

### Phase 2: Agent Design Flow

Goal: make agent creation a guided workflow instead of starting from raw YAML.

Deliverables:

- Add a **Design Agent** flow:
  1. Describe the business task.
  2. Select data boundaries and allowed systems.
  3. Recommend certified tools, skills, and pipelines.
  4. Generate an agent draft.
  5. Run a dry test with sample input.
  6. Produce validation findings and certification checklist.
- Add tool and skill recommendation cards with risk indicators.
- Add prompt/instruction diff view when regenerating an agent.
- Add artifact dependency graph for agents and pipelines.
- Add a test case panel for example inputs and expected outputs.

UX shape:

- Left panel: intent and constraints.
- Center panel: generated artifact and dependency graph.
- Right panel: validation, risk, and test evidence.

### Phase 3: Certification Workflow

Goal: support corporate governance around certified tools, skills, pipelines, and agents.

Deliverables:

- Add a certification tab for each artifact.
- Show ownership, version, hash, approval status, allowed capabilities, and required secrets.
- Add policy checklist:
  - input and output schema present
  - no prohibited tools
  - filesystem scope declared
  - network egress declared
  - secrets referenced safely
  - examples and tests present
  - last validation passed
- Add request-review and approve/reject actions.
- Add certification history timeline.
- Add certified-only filter to workspace and chat catalog.

Required backend support:

- Manifest certification metadata.
- Policy validation endpoint.
- Artifact signature or hash endpoint.
- Review event persistence.

### Phase 4: Runtime Operations Console

Goal: make local runs observable and controllable.

Deliverables:

- Replace execution history list with an operations console.
- Add live run timeline:
  - model call started/completed
  - tool call requested
  - policy decision
  - human approval requested
  - step completed
  - error/retry
- Add structured trace viewer with redaction indicators.
- Add run comparison across artifact versions.
- Add stop/cancel/retry controls where supported.
- Add retention controls and export options.

### Phase 5: Native Vector Database UI

Goal: make native vector database management visible, governable, and useful for agent design.

The current Lucene vector drivers already support local upsert, search, and enrichment through tool execution. The UI should turn that into a dedicated **Vector Database** surface, backed by explicit APIs rather than raw tool runs.

Deliverables:

- Add a Vector Database page that lists known Lucene indexes, their paths, document counts, dimensions, embedding strategy, size on disk, last updated time, and health.
- Add collection/index creation with safe defaults:
  - local storage path under the configured repository or datasets directory
  - embedding strategy selection
  - vector dimensions
  - metadata schema
  - retention policy
- Add document browser with filter, source, title, metadata, chunk text, vector status, and delete/reindex actions.
- Add import flows for CSV, JSONL, Markdown folders, web/RSS outputs, and pipeline outputs.
- Add search playground:
  - query text
  - `top_k`
  - similarity scores
  - retrieved context preview
  - metadata filters
  - copyable tool input payload
- Add retrieval evaluation tools:
  - saved test queries
  - expected source/document assertions
  - precision-style score summaries
  - before/after comparison after reindexing
- Add integration hooks in Agent Builder and Pipeline Designer so users can choose a certified vector index as context for an agent or as a pipeline step.
- Add governance controls:
  - certified indexes only for production agents
  - data classification labels
  - source lineage and ingestion history
  - retention/delete policy
  - warnings for indexes outside approved local paths

Initial UI shape:

- Left panel: collections/indexes with status, owner, and size.
- Center panel: documents/chunks and search results.
- Right panel: schema, ingestion history, policy, and retrieval test cases.

Required backend support:

- Native vector index registry, even if the first implementation stores metadata beside Lucene indexes.
- Index stats and health inspection APIs.
- CRUD APIs for documents/chunks.
- Search API that wraps `vector.lucene.search` behavior without requiring a tool manifest.
- Upsert/import APIs that wrap `vector.lucene.upsert` behavior and record source lineage.
- Optional migration path from tool-only vector usage to managed vector collections.

### Phase 6: MCP And External Chat Gateway

Goal: make external chatbot integration understandable and governable.

Deliverables:

- Add MCP gateway page with endpoint status, enabled transports, connected clients, and exposed artifacts.
- Add OpenAI-compatible proxy page with virtual model and function catalog preview.
- Add copyable client configuration snippets for supported clients.
- Add gateway policy controls:
  - expose certified artifacts only
  - expose tools, agents, pipelines separately
  - require API key
  - restrict network origins
  - limit tool call concurrency
- Add test console for MCP `tools/list`, `tools/call`, `prompts/list`, and `resources/list`.

### Phase 7: Corporate Hardening UX

Goal: make safe configuration the obvious path.

Deliverables:

- Add first-run setup wizard:
  - repo path
  - local model provider
  - llama.cpp server URL
  - API key policy
  - certified-only runtime mode
  - execution storage path
- Replace raw settings with grouped configuration pages:
  - Models
  - MCP Gateway
  - Security Policy
  - Secrets
  - Execution Storage
  - Artifact Repository
- Redact all secret values.
- Show configuration health checks.
- Add warning banners for unsafe defaults.

## Screen Inventory

Near-term screens:

- Workspace
- Chat
- Settings
- Usage
- Operations
- Certification
- Gateway
- Vector Database

Workspace should remain the main authoring surface. Chat should be the primary natural-language use surface. Operations, Certification, Gateway, and Vector Database should be explicit top-level destinations because they represent distinct corporate responsibilities.

## Component Backlog

Reusable components to add:

- `ArtifactStatusBadge`
- `PolicyStatusBadge`
- `CapabilityBadge`
- `CertificationPanel`
- `PolicyChecklist`
- `ExecutionTimeline`
- `TraceInspector`
- `SecretField`
- `ProviderStatusCard`
- `GatewayEndpointCard`
- `ArtifactDependencyGraph`
- `ValidationSummary`
- `VectorIndexList`
- `VectorIndexStatsCard`
- `VectorDocumentBrowser`
- `VectorSearchPlayground`
- `RetrievalEvaluationPanel`
- `IngestionHistoryTimeline`

## API Backlog For UI

The UI will need backend endpoints similar to:

- `GET /api/catalog/drivers`
- `GET /api/catalog/model-providers`
- `POST /api/policy/validate/{type}`
- `GET /api/artifacts/{type}/{name}/status`
- `GET /api/artifacts/{type}/{name}/certification`
- `POST /api/artifacts/{type}/{name}/certification/request`
- `POST /api/artifacts/{type}/{name}/certification/approve`
- `GET /api/gateway/mcp/status`
- `GET /api/gateway/openai/status`
- `POST /api/gateway/mcp/test`
- `GET /api/vector/indexes`
- `POST /api/vector/indexes`
- `GET /api/vector/indexes/{name}/stats`
- `GET /api/vector/indexes/{name}/documents`
- `POST /api/vector/indexes/{name}/documents:upsert`
- `POST /api/vector/indexes/{name}:search`
- `POST /api/vector/indexes/{name}:reindex`
- `DELETE /api/vector/indexes/{name}/documents/{id}`

## First Sprint Proposal

Sprint objective: remove UI/runtime drift and introduce visible governance primitives.

Tasks:

1. Add driver/provider catalog endpoints.
2. Wire `ToolWizard`, `AgentBuilder`, `SkillBuilder`, and chat model picker to catalog data.
3. Add artifact status badges in the sidebar and editor header.
4. Add validation summary panel below the editor header.
5. Add `SecretField` for settings and mask existing API key fields.
6. Add a first version of the Gateway page with MCP and OpenAI-compatible endpoint status.

Definition of done:

- No hardcoded backend driver IDs in the UI.
- `llama-cpp` displays and saves consistently.
- Invalid artifacts are visually obvious before run.
- Secrets are not rendered in plain text after load.
- Gateway status is visible without reading logs.

## Open Decisions

- Whether certification metadata lives in each manifest or in sidecar files.
- Whether artifact approval is Git commit based, runtime database based, or both.
- Whether the UI should support multiple repositories in one runtime.
- Which external chatbot clients are first-class targets.
- Whether corporate safe mode is always-on or a selectable runtime profile.
- Whether vector index metadata should live in manifests, sidecar files, or a runtime registry.
- Whether the initial embedding strategy should remain deterministic local hashing or move to provider-backed embedding models.
- Whether managed vector indexes should be artifact types, runtime resources, or both.