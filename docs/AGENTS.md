---
title: Agents Guide
---

# Agents Guide

Hubbers agents are currently stored as `AGENT.md` files with structured Markdown sections and JSON code blocks. They are not defined with YAML frontmatter in the bundled repository.

## Current Format

A typical agent document contains these sections:

- `## Configuration`
- `## Model`
- `## Input`
- `## Output`
- `## Instructions`

Representative example:

- [hubbers-repo/src/main/resources/repo/agents/universal.task/AGENT.md](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/agents/universal.task/AGENT.md:1)

## Minimal Example

````markdown
## Configuration

```json
{
  "name": "example.agent",
  "version": "1.0.0",
  "description": "Describe what this agent does",
  "tools": ["rss.fetch"],
  "maxIterations": 5,
  "timeoutSeconds": 60
}
```

## Model

```json
{
  "provider": "ollama",
  "name": "qwen3:4b",
  "temperature": 0.1
}
```

## Input

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "required": true
    }
  }
}
```

## Output

```json
{
  "type": "object",
  "properties": {
    "result": {
      "type": "string",
      "required": true
    }
  }
}
```

## Instructions

You are an assistant that answers the user's query with structured output.
````

## Section Semantics

### Configuration

Defines metadata and execution behavior.

Common fields seen in the current repo:

- `name`
- `version`
- `description`
- `tools`
- `maxIterations`
- `timeoutSeconds`

### Model

Defines the model provider, model name, and temperature.

Current code supports at least:

- `openai`
- `ollama`

### Input and output

These sections use JSON-schema-like objects. The runtime validates payloads before and after execution.

### Instructions

The Markdown body after `## Instructions` is the operative system prompt for the agent.

## When To Create An Agent

Create an agent when the task needs one or more of these:

- tool or pipeline calling
- conversation support
- structured input/output validation
- multi-step autonomous reasoning
- interactive forms or human-in-the-loop flows

If the task is mainly reusable methodology or prompting guidance, prefer a skill instead.

## Current Examples In The Repo

- `universal.task`: dynamic natural-language task runner
- `research.assistant`: tool-using research workflow
- `demo.search`: form-oriented example

## Testing

List available agents:

```bash
java -jar hubbers-distribution/target/hubbers.jar list agents
```

Run an agent directly:

```bash
java -jar hubbers-distribution/target/hubbers.jar agent run research.assistant --input '{"query":"What are the latest AI infrastructure trends?"}'
```

Run an agent in task mode:

```bash
java -jar hubbers-distribution/target/hubbers.jar agent run universal.task --request "Fetch and summarize an RSS feed"
```

## Related Docs

- [Agentic Architecture](AGENTIC_ARCHITECTURE.md)
- [Skills Guide](Skills.md)
- [Pipelines Guide](Pipelines.md)
