---
title: Agentic Architecture
---

# Agentic Architecture

This page documents how Hubbers currently handles tool-calling and autonomous execution.

## Terms

### Tool

A Hubbers tool is a repository artifact defined by `tool.yaml` and executed through a Java `ToolDriver`.

Example:

- [hubbers-repo/src/main/resources/repo/tools/rss.fetch/tool.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/tools/rss.fetch/tool.yaml:1)

### Function definition

At model-call time, tool manifests are converted into function definitions that the LLM can choose from.

### Function call

A function call is the LLM asking the runtime to execute a specific artifact with structured arguments.

### Agentic executor

`AgenticExecutor` is the runtime component that:

- loads the agent manifest
- builds the available function catalog
- manages conversation state
- executes tools and pipelines through the registry
- loops until it reaches a final answer or a configured limit

## Current Wiring

The current design uses `ExecutorRegistry` as the execution mediator:

- `ToolExecutor` is registered for tool execution
- `AgenticExecutor` is registered for agent execution
- `PipelineExecutor` is registered for pipeline execution
- `SkillExecutor` is registered for skill execution

This is important because older analysis documents described a circular dependency between agentic and pipeline execution. That is no longer the active design.

Reference:

- [Bootstrap.java](/Users/mauriziofarina/src/hubbers/hubbers-core/src/main/java/org/hubbers/app/Bootstrap.java:1)

## Natural Language Mode

`RuntimeFacade.runAgent()` detects the pattern:

```json
{
  "request": "Fetch the latest AI headlines",
  "context": {}
}
```

When present, execution is routed through `NaturalLanguageTaskService`.

Reference:

- [RuntimeFacade.java](/Users/mauriziofarina/src/hubbers/hubbers-core/src/main/java/org/hubbers/app/RuntimeFacade.java:1)

## Bundled Agent Types

The bundled repo currently demonstrates two different styles:

- `research.assistant`: an agent with an explicit tool list
- `universal.task`: a task-style entry point that plans and selects artifacts dynamically
- `demo.search`: a form-driven agent example

## Practical Mental Model

Use this model when reading or extending the code:

1. Agent manifest defines the model, input/output, and instructions.
2. Runtime builds the artifact/function catalog.
3. Model chooses a tool or pipeline through function calling.
4. Runtime executes the selected artifact.
5. Results are appended to the interaction context.
6. The loop ends when the model returns a final structured answer.

## Related Docs

- [Software Architecture](SWA.md)
- [Agents Guide](AGENTS.md)
- [Tools Guide](Tools.md)
- [Pipelines Guide](Pipelines.md)
