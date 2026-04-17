---
description: "YAML and Markdown artifact manifest standards for Hubbers. Use when: creating or editing agent manifests (AGENT.md), tool definitions (tool.yaml), pipeline configurations (pipeline.yaml), skill definitions (SKILL.md)."
applyTo: "**/repo/{agents,tools,pipelines,skills}/**/*.{yaml,yml,md}"
---

# Artifact Manifest Standards

## File Structure Requirements

### Agent Manifests (AGENT.md)

```markdown
---
agent:
  name: my.agent
  version: 1.0.0
  description: Brief one-line description of what this agent does
model:
  provider: openai
  name: gpt-4
  temperature: 0.7
input:
  type: object
  properties:
    query:
      type: string
      description: The search query
  required:
    - query
output:
  type: object
  properties:
    result:
      type: string
      description: The search result
  required:
    - result
---

## Instructions

Detailed instructions for the agent in Markdown format.

### Task

Describe what the agent should do.

### Examples

Provide examples of expected behavior.
```

**Critical Rules:**
- ✅ File MUST be named `AGENT.md` (all uppercase)
- ✅ YAML frontmatter between `---` markers
- ✅ Agent name format: `category.name` (lowercase, dot-separated)
- ✅ JSON Schema for input/output validation
- ✅ Markdown body after frontmatter for instructions
- ❌ No tabs in YAML (use 2 spaces for indentation)
- ❌ Don't use colons in descriptions without quoting

### Tool Manifests (tool.yaml)

```yaml
tool:
  name: my.tool
  version: 1.0.0
  description: Brief description of tool functionality
type: java  # or http, shell, python
config:
  class: org.hubbers.tool.MyToolDriver  # for Java tools
  # or
  endpoint: https://api.example.com     # for HTTP tools
  # or
  command: ./scripts/process.sh         # for shell tools
input:
  type: object
  properties:
    param1:
      type: string
      description: First parameter
  required:
    - param1
output:
  type: object
  properties:
    result:
      type: string
  required:
    - result
```

**Critical Rules:**
- ✅ File MUST be named `tool.yaml` (lowercase)
- ✅ Tool name format: `category.name`
- ✅ Type must be: `java`, `http`, `shell`, or `python`
- ✅ Config section matches the tool type
- ✅ Input/output schemas using JSON Schema

### Pipeline Manifests (pipeline.yaml)

```yaml
pipeline:
  name: my.pipeline
  version: 1.0.0
  description: Multi-step workflow description
input:
  type: object
  properties:
    url:
      type: string
  required:
    - url
output:
  type: object
  properties:
    summary:
      type: string
steps:
  - name: fetch
    type: tool
    tool: web.firecrawl
    input:
      url: "{{input.url}}"
    output: rawContent
  
  - name: analyze
    type: agent
    agent: text.summarizer
    input:
      text: "{{steps.fetch.output.rawContent}}"
    output: summary
```

**Critical Rules:**
- ✅ File MUST be named `pipeline.yaml` (lowercase)
- ✅ Steps execute sequentially
- ✅ Use `{{input.field}}` to reference pipeline input
- ✅ Use `{{steps.stepName.output.field}}` to reference previous step output
- ✅ Each step must specify `type` (agent, tool, pipeline, or skill)
- ✅ Each step must have unique `name`

### Skill Manifests (SKILL.md)

```markdown
---
skill:
  name: sentiment-analysis
  version: 1.0.0
  description: Analyze sentiment of text (positive, negative, neutral)
  tags:
    - nlp
    - sentiment
    - analysis
input:
  type: object
  properties:
    text:
      type: string
      description: Text to analyze
  required:
    - text
output:
  type: object
  properties:
    sentiment:
      type: string
      enum: [positive, negative, neutral]
    confidence:
      type: number
  required:
    - sentiment
    - confidence
---

# Sentiment Analysis Skill

## Instructions

Analyze the sentiment of the provided text and classify it as positive, negative, or neutral.

### Analysis Steps

1. Read the input text carefully
2. Identify emotional tone and language
3. Classify overall sentiment
4. Provide confidence score (0.0 to 1.0)

### Examples

**Input:** "This product is amazing! I love it!"
**Output:** {"sentiment": "positive", "confidence": 0.95}

**Input:** "It's okay, nothing special."
**Output:** {"sentiment": "neutral", "confidence": 0.70}
```

**Critical Rules:**
- ✅ File MUST be named `SKILL.md` (all uppercase)
- ✅ Follows agentskills.io specification
- ✅ Can include optional `scripts/` folder with executable code
- ✅ Skill name format: `kebab-case` (lowercase, hyphen-separated)
- ✅ YAML frontmatter + Markdown instructions
- ✅ Use tags for discoverability

## YAML Best Practices

### Indentation and Formatting

```yaml
# ✅ Use 2 spaces for indentation
agent:
  name: my.agent
  model:
    provider: openai
    name: gpt-4

# ❌ Never use tabs
agent:
	name: my.agent  # BAD!
```

### Quoting Strings

```yaml
# ✅ Quote strings with special characters
description: "Use when: analyzing sentiment"
description: "Format: JSON"
description: "Example: {\"key\": \"value\"}"

# ❌ Unquoted colons break parsing
description: Use when: analyzing sentiment  # BAD!

# ✅ Simple strings don't need quotes
name: sentiment-analysis
version: 1.0.0
```

### Multiline Strings

```yaml
# ✅ Use | for literal block (preserves newlines)
description: |
  This is a multi-line description.
  Each line is preserved.
  Including blank lines.

# ✅ Use > for folded block (joins lines)
description: >
  This is a long description
  that will be joined into
  a single line.

# ❌ Don't use quotes for multiline
description: "Line 1\nLine 2\nLine 3"  # Hard to read
```

### Lists and Arrays

```yaml
# ✅ Use hyphen for list items
required:
  - name
  - version

# ✅ Or inline array notation
required: [name, version]

# ✅ Nested lists
steps:
  - name: step1
    tools:
      - tool1
      - tool2
  - name: step2
    tools:
      - tool3
```

## JSON Schema Standards

### Common Patterns

```yaml
# String with validation
name:
  type: string
  minLength: 1
  maxLength: 100
  pattern: "^[a-z0-9.-]+$"

# Number with range
temperature:
  type: number
  minimum: 0.0
  maximum: 2.0

# Enum for fixed values
sentiment:
  type: string
  enum: [positive, negative, neutral]

# Array with items
tags:
  type: array
  items:
    type: string
  minItems: 1

# Nested object
model:
  type: object
  properties:
    provider:
      type: string
    name:
      type: string
  required:
    - provider
    - name

# Optional vs Required
input:
  type: object
  properties:
    required_field:
      type: string
    optional_field:
      type: string
  required:
    - required_field
```

### Use Descriptions

```yaml
# ✅ Add descriptions for clarity
input:
  type: object
  properties:
    query:
      type: string
      description: "The search query to execute (e.g., 'latest AI news')"
    maxResults:
      type: integer
      description: "Maximum number of results to return (default: 10)"
      default: 10

# ❌ Don't skip descriptions
input:
  type: object
  properties:
    query:
      type: string
    maxResults:
      type: integer
```

## Naming Conventions

### Agent Names

Format: `category.action`

Examples:
- ✅ `demo.search` - Search demo agent
- ✅ `research.assistant` - Research assistant agent
- ✅ `universal.task` - Universal task handler
- ❌ `searchAgent` - Use dots, not camelCase
- ❌ `research-assistant` - Use dots, not hyphens

### Tool Names

Format: `category.service`

Examples:
- ✅ `web.firecrawl` - Web scraping with Firecrawl
- ✅ `rss.reader` - RSS feed reader
- ✅ `file.reader` - File reading utility
- ❌ `firecrawlTool` - Use dots, not camelCase

### Pipeline Names

Format: `category.workflow`

Examples:
- ✅ `rss.sentiment-analysis` - RSS sentiment analysis workflow
- ✅ `web.scrape-and-analyze` - Web scraping and analysis
- ❌ `rssSentiment` - Use dots and hyphens

### Skill Names

Format: `kebab-case`

Examples:
- ✅ `sentiment-analysis` - Sentiment analysis skill
- ✅ `ner-extraction` - Named entity recognition
- ✅ `text-summarizer` - Text summarization
- ❌ `sentimentAnalysis` - Use hyphens, not camelCase
- ❌ `sentiment.analysis` - Use hyphens, not dots

## Version Management

### Semantic Versioning

```yaml
# ✅ Use semantic versioning
version: 1.0.0  # MAJOR.MINOR.PATCH

# Increment rules:
# MAJOR - Breaking changes to input/output schema
# MINOR - New features, backward compatible
# PATCH - Bug fixes, no schema changes
```

## Model Configuration

### OpenAI Models

```yaml
model:
  provider: openai
  name: gpt-4  # or gpt-4-turbo, gpt-3.5-turbo
  temperature: 0.7
  maxTokens: 2000  # optional
  topP: 1.0        # optional
```

### Ollama Models

```yaml
model:
  provider: ollama
  name: llama2  # or mistral, codellama, etc.
  temperature: 0.8
  baseUrl: http://localhost:11434  # optional, defaults to localhost
```

### Anthropic Models

```yaml
model:
  provider: anthropic
  name: claude-3-opus  # or claude-3-sonnet, claude-3-haiku
  temperature: 0.7
  maxTokens: 4000
```

## Variable Interpolation

### In Pipelines

```yaml
steps:
  - name: fetch
    input:
      url: "{{input.url}}"           # Pipeline input
  
  - name: process
    input:
      data: "{{steps.fetch.output}}"  # Previous step output
      param: "{{env.API_KEY}}"        # Environment variable
```

### In Agent Instructions

```markdown
## Instructions

Process the following query: {{input.query}}

Use these parameters:
- Max results: {{input.maxResults}}
- Language: {{input.language}}
```

## Validation Checklist

Before committing manifests:

- [ ] File named correctly (`AGENT.md`, `tool.yaml`, `pipeline.yaml`, `SKILL.md`)
- [ ] YAML frontmatter properly formatted (no tabs, proper indentation)
- [ ] No unquoted colons in descriptions
- [ ] JSON schemas for input and output
- [ ] All required fields present
- [ ] Naming conventions followed
- [ ] Version number specified
- [ ] Descriptions are clear and helpful
- [ ] Examples provided in Markdown body (for agents/skills)
- [ ] Test with: `hubbers <type> run <name> --input '{...}'`

## Common Errors and Fixes

### YAML Parsing Errors

```yaml
# ❌ Error: unquoted colon
description: Use when: analyzing text

# ✅ Fix: quote the string
description: "Use when: analyzing text"

# ❌ Error: tabs used for indentation
agent:
	name: my.agent

# ✅ Fix: use 2 spaces
agent:
  name: my.agent

# ❌ Error: missing space after colon
name:my.agent

# ✅ Fix: add space
name: my.agent
```

### Schema Validation Errors

```yaml
# ❌ Error: missing type
properties:
  name:
    description: Agent name

# ✅ Fix: specify type
properties:
  name:
    type: string
    description: Agent name

# ❌ Error: required field not in properties
input:
  type: object
  properties:
    query:
      type: string
  required:
    - query
    - maxResults  # But maxResults not in properties!

# ✅ Fix: add all required fields to properties
input:
  type: object
  properties:
    query:
      type: string
    maxResults:
      type: integer
  required:
    - query
    - maxResults
```

## Template Quick Reference

### Minimal Agent

```yaml
---
agent:
  name: category.name
  version: 1.0.0
  description: What this agent does
model:
  provider: openai
  name: gpt-4
  temperature: 0.7
input:
  type: object
  properties: {}
output:
  type: object
  properties: {}
---

## Instructions

Agent instructions here.
```

### Minimal Tool

```yaml
tool:
  name: category.name
  version: 1.0.0
  description: What this tool does
type: java
config:
  class: org.hubbers.tool.MyDriver
input:
  type: object
  properties: {}
output:
  type: object
  properties: {}
```

### Minimal Pipeline

```yaml
pipeline:
  name: category.workflow
  version: 1.0.0
  description: What this pipeline does
input:
  type: object
  properties: {}
output:
  type: object
  properties: {}
steps:
  - name: step1
    type: tool
    tool: some.tool
    input: {}
```

### Minimal Skill

```yaml
---
skill:
  name: skill-name
  version: 1.0.0
  description: What this skill does
input:
  type: object
  properties: {}
output:
  type: object
  properties: {}
---

# Skill Name

## Instructions

Skill instructions here.
```
