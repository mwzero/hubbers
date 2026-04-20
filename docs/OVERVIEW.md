---
title: Project Overview
---

# Project Overview

Hubbers is a Git-native Java runtime for executing repository-defined AI artifacts:

- agents
- tools
- pipelines
- skills

The codebase is organized as a multi-module Maven project with a bundled artifact repository and a packaged distribution.

## Modules

| Module | Purpose |
| --- | --- |
| `hubbers-core` | Runtime core, executors, validators, model providers |
| `hubbers-tools-builtin` | Built-in Java tool drivers and Lucene support classes |
| `hubbers-framework` | Compatibility jar preserving the historical runtime coordinate |
| `hubbers-web` | Web server and packaged frontend resources |
| `hubbers-cli` | CLI commands and `org.hubbers.Main` entrypoint |
| `hubbers-repo` | Bundled sample repo with manifests and config |
| `hubbers-ui` | React/Vite frontend packaged for the web module |
| `hubbers-distribution` | Shaded jar packaging |

## Bundled Artifact Inventory

Current contents of `hubbers-repo/src/main/resources/repo`:

- 3 agents
- 15 tools
- 11 pipelines
- 6 skills

## Runtime Highlights

- unified execution through `RuntimeFacade`
- tool-calling agents through `AgenticExecutor`
- declarative orchestration through `PipelineExecutor`
- reusable skill execution through `SkillExecutor`
- form-driven interaction in the web layer
- execution logging and persisted run history

## Build Entry Point

```bash
mvn clean package
java -jar hubbers-distribution/target/hubbers.jar --help
```

## Related Docs

- [Software Architecture](SWA.md)
- [Agentic Architecture](AGENTIC_ARCHITECTURE.md)
- [GitHub Pages Publishing](GITHUB_PAGES.md)
