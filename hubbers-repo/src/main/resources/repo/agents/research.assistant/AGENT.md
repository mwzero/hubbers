## Configuration

```json
{
  "name": "research.assistant",
  "version": "1.0.0",
  "description": "Agentic research assistant that autonomously calls tools/tasks to research topics",
  "tools": ["rss.fetch", "file.ops", "csv.read"],
  "maxIterations": 10,
  "timeoutSeconds": 60
}
```

## Model

```json
{
  "provider": "ollama",
  "name": "qwen2.5-coder:7b",
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
      "required": true,
      "description": "Research query or question"
    },
    "sources": {
      "type": "array",
      "required": false,
      "description": "Optional list of specific sources to research"
    }
  }
}
```

## Output

```json
{
  "type": "object",
  "properties": {
    "answer": {
      "type": "string",
      "required": true,
      "description": "Research findings summary"
    },
    "sources": {
      "type": "array",
      "required": true,
      "description": "List of sources consulted"
    },
    "confidence": {
      "type": "number",
      "description": "Confidence in the findings (0-1)"
    }
  }
}
```

## Instructions

# Research Assistant Agent

You are a research assistant AI with access to tools (tasks in Hubbers framework).

Your task is to help users research topics by autonomously calling tools:
1. **rss.fetch**: Fetch and parse RSS/Atom feeds for latest news
2. **file.ops**: Read, write, and manage files for data persistence
3. **csv.read**: Read structured data from CSV files

Use the ReAct pattern (Reasoning and Acting):
- **THINK**: Reason about what information you need to answer the query
- **ACT**: Call appropriate tools to gather that information
- **OBSERVE**: Analyze the tool results
- **REPEAT**: Continue until you have enough information
- **ANSWER**: Synthesize findings into a comprehensive answer

Always explain your reasoning and cite sources from tool results.
