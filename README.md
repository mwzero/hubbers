# Hubbers

Hubbers is a Git-native Java runtime for executing AI artifacts stored in a repository:

- `agents` for autonomous and prompt-driven execution
- `tools` for deterministic capabilities backed by Java drivers
- `pipelines` for declarative orchestration
- `skills` for reusable methodologies and prompt assets

The project is organized as a multi-module Maven build and ships both a CLI/runtime and a bundled artifact repository.

## What The Codebase Contains

Current modules:

- `hubbers-core`: runtime core, executors, manifest parsing, model providers, validation
- `hubbers-tools-builtin`: built-in Java tool drivers discovered through `ServiceLoader`
- `hubbers-framework`: compatibility jar that preserves the historical runtime coordinate
- `hubbers-web`: web server and packaged frontend resources
- `hubbers-cli`: command-line interface and `org.hubbers.Main`
- `hubbers-repo`: bundled sample repository with agents, tools, pipelines, skills, and config
- `hubbers-ui`: React/Vite frontend packaged as build output for the web module
- `hubbers-distribution`: shaded executable distribution

Current bundled artifacts in `hubbers-repo/src/main/resources/repo`:

- 3 agents
- 15 tools
- 11 pipelines
- 6 skills

## Architecture Snapshot

The current runtime centers on `RuntimeFacade`, which loads artifacts from `ArtifactRepository` and dispatches execution to:

- `AgenticExecutor` for tool-calling agent execution
- `ToolExecutor` for driver-based tool execution
- `PipelineExecutor` for step orchestration
- `SkillExecutor` for skill execution

The older circular dependency concern between `AgenticExecutor` and `PipelineExecutor` has already been addressed in code with `ExecutorRegistry` wiring in [Bootstrap.java](/Users/mauriziofarina/src/hubbers/hubbers-core/src/main/java/org/hubbers/app/Bootstrap.java:1).

## Quick Start

Requirements:

- Java 21
- Maven 3.9+
- `OPENAI_API_KEY` only if you want to use OpenAI-backed models
- Ollama if you want to use the default local model configuration

Build the full project:

```bash
mvn clean package
```

Run the shaded distribution:

```bash
java -jar hubbers-distribution/target/hubbers.jar --help
```

Run a few common commands:

```bash
java -jar hubbers-distribution/target/hubbers.jar list agents
java -jar hubbers-distribution/target/hubbers.jar list tools
java -jar hubbers-distribution/target/hubbers.jar agent run universal.task --request "Fetch the latest items from an RSS feed"
java -jar hubbers-distribution/target/hubbers.jar tool run rss.fetch --input '{"feeds":["https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml"],"limit":5}'
java -jar hubbers-distribution/target/hubbers.jar web --port 7070
```

## Repository Layout

```text
.
├── hubbers-distribution/
├── hubbers-cli/
├── hubbers-core/
├── hubbers-framework/
├── hubbers-tools-builtin/
├── hubbers-repo/
│   └── src/main/resources/repo/
│       ├── agents/
│       ├── tools/
│       ├── pipelines/
│       ├── skills/
│       ├── application.yaml
│       └── _executions/
├── hubbers-ui/
├── hubbers-web/
├── docs/
└── CODEBASE_ANALYSIS.md
```

## Documentation

The documentation set has been reorganized to be publishable from the `docs/` folder via GitHub Pages.

Start here:

- [Documentation Home](docs/index.md)
- [Software Architecture](docs/SWA.md)
- [Agentic Architecture](docs/AGENTIC_ARCHITECTURE.md)
- [Agents Guide](docs/AGENTS.md)
- [Tools Guide](docs/Tools.md)
- [Pipelines Guide](docs/Pipelines.md)
- [Skills Guide](docs/Skills.md)
- [Native Build Guide](docs/NATIVE_BUILD.md)
- [GitHub Pages Publishing Guide](docs/GITHUB_PAGES.md)
- [Codebase Analysis](CODEBASE_ANALYSIS.md)

## Current Caveats

Two implementation details are worth knowing before publishing or packaging:

- `hubbers-web` serves frontend assets copied from `hubbers-ui` build output during the Maven build; if you skip the frontend build, you need an existing `hubbers-ui/dist`.
- Repo-path defaults are not fully consistent across the codebase. The CLI defaults to `hubbers-repo/src/main/resources/repo`, while some lower-level constructors still default to `repo`.

Those observations are documented in more detail in [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md).
