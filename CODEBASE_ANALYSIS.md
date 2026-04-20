# Hubbers Codebase Analysis

**Review date:** April 20, 2026  
**Scope:** runtime architecture, artifact model, build surface, and documentation readiness

## Findings

### 1. Embedded web assets appear out of sync with the copied frontend build

Severity: high

The checked-in `index.html` under the framework resources references hashed asset filenames that are not present in the same directory.

- [hubbers-framework/src/main/resources/web/index.html](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/resources/web/index.html:15)
- [hubbers-framework/src/main/resources/web/assets](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/resources/web/assets)

Observed mismatch:

- `index.html` references `/assets/index-D6RfzPcg.js` and `/assets/index-CwpJfaSz.css`
- the directory currently contains different hashes such as `index-Cq4g9_Sa.js` and `index-K0ZV1QCZ.css`

Impact:

- the embedded web UI can fail at runtime even though the documentation site publishes correctly
- this also makes it unsafe to describe the bundled UI as production-ready without a rebuild

Recommended follow-up:

- rebuild `hubbers-ui`
- copy fresh assets into `hubbers-framework/src/main/resources/web`
- verify `hubbers web` against the packaged distribution

### 2. Default repo path assumptions are inconsistent across entry points

Severity: medium

There are two active defaults in the codebase:

- [hubbers-framework/src/main/java/org/hubbers/app/Bootstrap.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/app/Bootstrap.java:45) defaults to `repo`
- [hubbers-framework/src/main/java/org/hubbers/config/ConfigLoader.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/config/ConfigLoader.java:17) defaults to `repo`
- [hubbers-framework/src/main/java/org/hubbers/cli/HubbersCommand.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/cli/HubbersCommand.java:62) documents `hubbers-repo/src/main/resources/repo` as the default repo path
- [hubbers-framework/src/main/java/org/hubbers/web/WebServer.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/web/WebServer.java:39) also assumes `hubbers-repo/src/main/resources/repo`

Impact:

- library-style use of `Bootstrap.createRuntimeFacade()` can behave differently from CLI usage
- tests and examples can drift depending on working directory and packaging layout

Recommended follow-up:

- choose a single canonical default
- centralize it in one constant
- align CLI help text, `WebMain`, `Bootstrap`, and `ConfigLoader`

### 3. Documentation had drifted materially from the current artifact formats

Severity: medium

Before this refresh, the docs described artifact formats that no longer match the repository:

- agents were documented as YAML frontmatter documents, but the repo currently uses sectioned Markdown with JSON code blocks
- tools were documented with a `driver.class` manifest field, but runtime dispatch is now based on `type`
- older docs still referenced removed simple agents such as `rss.sentiment`

Representative current artifacts:

- [hubbers-repo/src/main/resources/repo/agents/universal.task/AGENT.md](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/agents/universal.task/AGENT.md:1)
- [hubbers-repo/src/main/resources/repo/tools/rss.fetch/tool.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/tools/rss.fetch/tool.yaml:1)
- [hubbers-repo/src/main/resources/repo/pipelines/rss.sentiment.csv/pipeline.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/pipelines/rss.sentiment.csv/pipeline.yaml:1)
- [hubbers-repo/src/main/resources/repo/skills/sentiment-analysis/SKILL.md](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/skills/sentiment-analysis/SKILL.md:1)

Impact:

- external users would create incompatible artifacts
- GitHub Pages publication would amplify outdated guidance

Resolution:

- the docs set has been rewritten around the current implementation and repository samples

## Current Architecture Assessment

### Summary

Hubbers has a solid execution model and cleaner architecture than some legacy documentation suggested. The current implementation is closer to a usable runtime than the previous docs implied.

Strengths:

- clear module split between runtime, repository, UI, and distribution
- `RuntimeFacade` provides a single execution API for CLI and web layers
- the circular executor wiring issue has already been addressed using `ExecutorRegistry`
- tool execution remains cleanly extensible through driver registration
- skills, forms, and execution history are now first-class runtime concepts

Constraints:

- frontend packaging needs verification before claiming release readiness
- repo-path defaults should be unified
- there are checked-in `_executions` artifacts inside the bundled repo, which should probably be excluded from published examples

### Module Inventory

- `hubbers-framework`: 115 Java source files, 15 test files
- `hubbers-ui`: Vite/React frontend copied into framework resources during Maven build
- `hubbers-repo`: bundled sample repository with runtime config and artifacts
- `hubbers-distribution`: shaded executable jar assembly

Bundled artifact counts:

- 3 agents
- 15 tools
- 11 pipelines
- 6 skills

### Runtime Flow

1. CLI or web entry point creates a `RuntimeFacade`.
2. `ConfigLoader` reads `application.yaml` from the selected repo root.
3. `ArtifactRepository` loads manifests from `agents`, `tools`, `pipelines`, and `skills`.
4. `RuntimeFacade` dispatches to `AgenticExecutor`, `ToolExecutor`, `PipelineExecutor`, or `SkillExecutor`.
5. `ExecutionStorageService` persists execution metadata, logs, and payloads under `_executions`.

Key references:

- [RuntimeFacade.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/app/RuntimeFacade.java:25)
- [Bootstrap.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/app/Bootstrap.java:39)
- [HubbersCommand.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/cli/HubbersCommand.java:32)

## Build And Publication Readiness

### What Is Ready

- the Maven module structure is clear and publishable
- the docs are now structured for GitHub Pages from `docs/`
- the architecture and artifact guides now reflect the actual repository

### What Still Needs Code Work

- rebuild and verify the embedded UI bundle
- decide whether GitHub Pages should publish only docs or also a static demo frontend
- if a static frontend is desired, set `vite.base` for Pages deployment and separate API-dependent runtime UI from docs publishing

### Recommendation

Publish the documentation site from `docs/` now. Do not present the runtime web UI as a GitHub Pages deliverable until the frontend asset pipeline and base-path behavior are corrected.
