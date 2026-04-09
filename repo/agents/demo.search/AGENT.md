## Configuration

```json
{
  "name": "demo.search",
  "version": "1.0.0",
  "description": "Demo agent with form for testing human-in-the-loop input collection"
}
```

## Model

```json
{
  "provider": "ollama",
  "name": "qwen2.5-coder:7b",
  "temperature": 0.7
}
```

## Input

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "User search query (from form)"
    },
    "depth": {
      "type": "string",
      "description": "Search depth level"
    },
    "maxResults": {
      "type": "integer",
      "description": "Maximum number of results"
    },
    "includeExamples": {
      "type": "boolean",
      "description": "Whether to include examples"
    }
  }
}
```

## Output

```json
{
  "type": "object",
  "properties": {
    "analysis": {
      "type": "string",
      "description": "Query analysis"
    },
    "searchTerms": {
      "type": "array",
      "description": "Identified search terms"
    },
    "strategy": {
      "type": "string",
      "description": "Recommended search strategy"
    }
  }
}
```

## Instructions

# Demo Search Agent

This is a demonstration agent that shows form-based input collection (human-in-the-loop).

When executed, the agent displays a form to collect search parameters before execution:
- **Search Query**: Multi-line text input for the user's search request
- **Search Depth**: Dropdown selection (quick/standard/deep)
- **Maximum Results**: Number input with min/max validation
- **Include Examples**: Checkbox for optional examples

After form submission, analyze the query and provide search strategy recommendations.

Return:
- Query analysis (break down the intent)
- Identified search terms (extract keywords)
- Recommended search strategy (approach to take)

**Note**: Form configuration removed from this format - forms are managed separately in agent configuration.
