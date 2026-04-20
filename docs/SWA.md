---
title: Software Architecture
---

# Software Architecture

Hubbers is a multi-module Java 21 project that turns a repository of AI artifacts into an executable runtime. The current implementation is artifact-driven, CLI-first, and extendable through tool drivers and model providers.

## Modules

| Module | Purpose |
| --- | --- |
| `hubbers-framework` | Runtime core, executors, manifest parsing, CLI, web API |
| `hubbers-repo` | Bundled repository with sample artifacts and runtime config |
| `hubbers-ui` | React/Vite frontend copied into framework resources at build time |
| `hubbers-distribution` | Shaded executable distribution |

## Execution Model

The runtime centers on `RuntimeFacade`, which provides a single API for all artifact types.

Execution flow:

1. CLI or web layer resolves the repo path.
2. `ConfigLoader` loads `application.yaml`.
3. `ArtifactRepository` loads artifact definitions from disk.
4. `RuntimeFacade` dispatches execution.
5. Execution metadata, input, output, and logs are written under `_executions`.

## Core Runtime Components

| Component | Role |
| --- | --- |
| `RuntimeFacade` | Unified entry point for agents, tools, pipelines, and skills |
| `ArtifactRepository` | Lists and loads manifests from the repo root |
| `AgenticExecutor` | Runs tool-calling agents with conversation support |
| `ToolExecutor` | Dispatches to Java tool drivers by manifest `type` |
| `PipelineExecutor` | Runs declarative multi-step workflows |
| `SkillExecutor` | Executes reusable skills and hybrid skill flows |
| `ExecutionStorageService` | Persists run metadata and logs |
| `ManifestValidator` | Validates artifacts before execution |

## Dependency Wiring

The executor graph is now mediated through `ExecutorRegistry`, which removes the older direct circular dependency between agentic and pipeline execution.

Key wiring reference:

- [Bootstrap.java](/Users/mauriziofarina/src/hubbers/hubbers-framework/src/main/java/org/hubbers/app/Bootstrap.java:45)

## Artifact Types

Hubbers currently supports four repository artifact categories:

| Type | Storage format | Purpose |
| --- | --- | --- |
| Agents | `AGENT.md` with structured sections | Prompted or agentic execution |
| Tools | `tool.yaml` | Deterministic executable capabilities |
| Pipelines | `pipeline.yaml` | Orchestration across artifacts |
| Skills | `SKILL.md` with structured sections | Reusable methodologies |

## Built-In Runtime Features

- Natural-language routing through `runAgent(..., {"request": ...})`
- Conversation-aware execution for agentic workflows
- Human-in-the-loop forms via `forms.before`
- Execution history APIs
- OpenAI and Ollama model provider support
- Built-in file, shell, RSS, process, CSV, Lucene, browser, and user-input tools

## Known Architectural Constraints

- The web UI bundle in framework resources should be rebuilt and verified before treating it as release-ready.
- Repo-path defaults are not fully unified across all entry points.
- The bundled repo contains checked-in execution artifacts under `_executions`, which is convenient for development but noisy for published samples.

## Related Docs

- [Agentic Architecture](AGENTIC_ARCHITECTURE.md)
- [Agents Guide](AGENTS.md)
- [Tools Guide](Tools.md)
- [Pipelines Guide](Pipelines.md)
- [Skills Guide](Skills.md)
- [Native Build Guide](NATIVE_BUILD.md)
