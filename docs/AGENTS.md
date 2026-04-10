# Agent Development Guide

Documentation for creating and modifying agents in the Hubbers framework.

## Agent Format

Hubbers uses the **Markdown format** (`AGENT.md`) with YAML frontmatter for defining agents.

This format follows the [agents.md](https://agents.md/) specification, providing a standardized way to define AI agents that works across multiple tools and platforms.

### Markdown Format (`AGENT.md`)

Enhanced Markdown format with YAML frontmatter:

```markdown
---
agent:
  name: my-agent
  version: 1.0.0
  description: What this agent does

model:
  provider: ollama
  name: model-id
  temperature: 0.1

input:
  schema:
    type: object
    properties:
      field_name: {type: string, required: true}

output:
  schema:
    type: object
    properties:
      result: {type: string, required: true}

tools: []
---

# My Agent

## Instructions

Your detailed instructions here...
This section replaces the system_prompt field.

## Testing Notes

How to test this agent...

## Architecture Notes

Implementation details...
```

**Key Benefits:**
- Uses Markdown body for instructions (more readable than YAML multiline strings)
- Can include rich documentation sections (## Testing, ## Architecture)
- Supports Git diffs better (Markdown formatting)
- Follows the [agents.md](https://agents.md/) open specification
- Compatible with many AI coding tools and platforms

## Field Reference

### Required Fields

#### `agent`
Metadata about the agent:
- `name` (string): Unique identifier, kebab-case (e.g., "text-summarizer")
- `version` (string): Semantic version (e.g., "1.0.0")
- `description` (string): Human-readable description (1-2 sentences)

#### `model`
LLM configuration:
- `provider` (string): "ollama" (local) or "openai" (cloud)
- `name` (string): Model identifier
  - Ollama: "qwen3:4b", "llama3:8b", "qwen2.5-coder:7b"
  - OpenAI: "gpt-4", "gpt-4-turbo", "gpt-3.5-turbo"
- `temperature` (float): 0.0-1.0
  - 0.0-0.2: Precise, deterministic
  - 0.3-0.7: Balanced
  - 0.8-1.0: Creative, varied

#### `input`
Input validation schema (JSON Schema):
```yaml
input:
  schema:
    type: object
    properties:
      propertyName:
        type: string|number|object|array|boolean
        required: true|false
        description: "Field description"
```

#### `output`
Output validation schema (JSON Schema):
```yaml
output:
  schema:
    type: object
    properties:
      result:
        type: string
        required: true
```

#### `instructions`
Agent behavior and system prompt - defined in the Markdown body (not in YAML frontmatter):

Use the `## Instructions` heading in the Markdown body:

```markdown
## Instructions

Your detailed instructions for the LLM.
Explain the role, constraints, output format, etc.
```

This section replaces the old `instructions.system_prompt` field from the YAML-only format.

### Optional Fields

#### `tools`
List of available tools/agents/pipelines:
```yaml
tools:
  - rss.fetch
  - file.ops
  - csv.read
```

Leave empty `[]` for simple agents without tool access.

#### `config`
Advanced configuration (agentic agents):
```yaml
config:
  tools: []                    # Tool list (alternative to top-level)
  max_iterations: 15           # Max ReAct loop cycles
  timeout_seconds: 120         # Execution timeout
```

#### `forms`
Interactive input collection (human-in-the-loop):
```yaml
forms:
  before:
    title: "Form Title"
    description: "Form description"
    fields:
      - name: query
        type: text|textarea|number|select|checkbox
        label: "Field Label"
        required: true
        placeholder: "Hint text"
```

Field types:
- `text`: Single-line input
- `textarea`: Multi-line input
- `number`: Numeric input (with min/max/step)
- `select`: Dropdown (requires options array)
- `checkbox`: Boolean

#### `examples`
Usage examples for testing and documentation:
```yaml
examples:
  - name: example-name
    description: What this example demonstrates
    input:
      field: "value"
    output:
      result: "expected output"
```

## Agent Patterns

### Pattern 1: Simple Agent (Single-Shot)

**Use case**: Direct LLM call without tools
**Examples**: text.summarizer, rss.sentiment, rss.ner

**Characteristics:**
- No `tools` or `config` sections
- Simple input/output schemas
- Temperature 0.1 (deterministic)
- Strict system prompt (JSON-only responses)

**Template:**
```yaml
agent:
  name: simple-agent
  version: 1.0.0
  description: Does one thing well

model:
  provider: ollama
  name: qwen3:4b
  temperature: 0.1

instructions:
  system_prompt: |
    You are a specialized assistant.
    Respond ONLY with JSON: {"result": "..."}

input:
  schema:
    type: object
    properties:
      input_field: {type: string, required: true}

output:
  schema:
    type: object
    properties:
      result: {type: string, required: true}

tools: []
```

### Pattern 2: Agentic Agent (ReAct Loop)

**Use case**: Multi-turn reasoning with tool calling
**Examples**: universal.task, research.assistant

**Characteristics:**
- `config` section with iteration limits
- ReAct pattern in system prompt
- Flexible input (usually `request: string`)
- Output includes `tools_used` and `reasoning`

**Template:**
```yaml
agent:
  name: agentic-agent
  version: 1.0.0
  description: Autonomous task execution

model:
  provider: ollama
  name: qwen2.5-coder:7b
  temperature: 0.1

instructions:
  system_prompt: |
    You are an autonomous agent with access to tools.
    
    ReAct Pattern:
    1. THINK: Reason about the task
    2. ACT: Call appropriate tools
    3. OBSERVE: Analyze results
    4. REPEAT: Continue until complete
    
    Always structure output as:
    {
      "result": {...},
      "tools_used": ["tool1", "tool2"],
      "reasoning": "explanation"
    }

config:
  tools: []                    # Injected dynamically
  max_iterations: 15
  timeout_seconds: 120

input:
  schema:
    type: object
    properties:
      request:
        type: string
        required: true
        description: Natural language request

output:
  schema:
    type: object
    properties:
      result: {type: object, required: true}
      tools_used: {type: array, required: true}
      reasoning: {type: string, required: true}
```

### Pattern 3: Interactive Agent (Forms)

**Use case**: Human-in-the-loop data collection
**Examples**: demo.search

**Characteristics:**
- `forms.before` section for input
- Variable substitution in system_prompt (${variable})
- Rich UI field definitions

**Template:**
```yaml
agent:
  name: interactive-agent
  version: 1.0.0
  description: Collects user input via forms

model:
  provider: ollama
  name: qwen3:4b
  temperature: 0.7

instructions:
  system_prompt: |
    User requested: ${query}
    Search depth: ${depth}
    
    Analyze and provide results...

forms:
  before:
    title: "Configuration"
    description: "Set your preferences"
    fields:
      - name: query
        type: textarea
        label: "Search Query"
        required: true
      
      - name: depth
        type: select
        label: "Search Depth"
        options:
          - label: "Quick"
            value: "quick"
          - label: "Deep"
            value: "deep"

input:
  schema:
    type: object
    properties:
      query: {type: string, required: true}
      depth: {type: string, required: true}

output:
  schema:
    type: object
    properties:
      results: {type: array}
```

## Testing Agents

### Manual Testing

```bash
# Test with inline JSON
hubbers agent run my-agent --input '{"field":"value"}'

# Test with file
echo '{"field":"value"}' > input.json
hubbers agent run my-agent --input input.json

# Check output
# Success: JSON output printed
# Failure: Error message to stderr, exit code 1
```

### Unit Testing

Create test in `src/test/java/org/hubbers/agent/`:

```java
@Test
void testMyAgent() {
    // Load agent
    AgentManifest manifest = repository.loadAgent("my-agent");
    
    // Prepare input
    JsonNode input = mapper.createObjectNode()
        .put("field", "value");
    
    // Execute
    RunResult result = executor.execute(manifest, input);
    
    // Assert
    assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
    assertNotNull(result.getOutput().get("result"));
}
```

### Validation Testing

Input/output schemas are validated automatically:
- Invalid input → `RunResult.failed("validation error")`
- Invalid output → `RunResult.failed("validation error")`

Test edge cases:
- Missing required fields
- Wrong data types
- Out-of-range values

## Best Practices

### System Prompt Guidelines

1. **Be specific**: Clear role and constraints
2. **Define format**: Exact JSON structure expected
3. **Include examples**: Show input/output pairs
4. **Set boundaries**: "ONLY respond with JSON", "No explanations"
5. **Handle variables**: Use `${variable}` for form field substitution

### Schema Design

1. **Required fields**: Mark essential fields as `required: true`
2. **Descriptions**: Add clear descriptions to all properties
3. **Type safety**: Use specific types (string, number, etc.)
4. **Array items**: Define structure for array elements
5. **Object properties**: Nest schemas for complex objects

### Temperature Selection

- **0.0-0.1**: Data extraction, classification, strict formatting
- **0.1-0.3**: Summarization, translation, factual tasks
- **0.4-0.7**: General reasoning, creative analysis
- **0.8-1.0**: Creative writing, brainstorming (rarely needed)

### Tool Integration

For agentic agents:
1. List tools in `config.tools` or leave empty for dynamic injection
2. Document tool usage in system prompt
3. Explain when to use each tool
4. Set appropriate `max_iterations` (typical: 10-20)
5. Set timeout (typical: 60-180 seconds)

## Common Patterns

### JSON-Only Output

```yaml
instructions:
  system_prompt: |
    Respond EXCLUSIVELY with JSON, no other text.
    Format: {"result": "..."}
```

### Preserved Fields

When processing data that must be preserved:

```yaml
instructions:
  system_prompt: |
    Process the input and return it with additional fields.
    Preserve original fields: url, date, link
    Add new field: sentiment
```

### Batch Processing

```yaml
input:
  schema:
    type: object
    properties:
      items:
        type: array
        items:
          type: object
          properties:
            text: {type: string}

output:
  schema:
    type: object
    properties:
      results:
        type: array
        items:
          type: object
          properties:
            text: {type: string}
            result: {type: string}
```

## Examples

See these agents for reference:
- [text.summarizer](text.summarizer/) - Simple agent (both formats)
- [universal.task](universal.task/) - Agentic agent
- [demo.search](demo.search/) - Interactive agent with forms
- [rss.sentiment](rss.sentiment/) - Batch processing
- [research.assistant](research.assistant/) - Tool-calling agent

## Troubleshooting

### Agent Not Loading

- Check file name: `AGENT.md` (case-sensitive, all uppercase)
- Verify directory structure: `repo/agents/<name>/AGENT.md`
- Run `hubbers list agents` to verify discovery

### Invalid Schema

- Use JSON Schema validator: https://www.jsonschemavalidator.net/
- Check required vs optional fields
- Verify type names (string, number, object, array, boolean)

### Model Errors

- Ollama: Ensure service is running (`ollama serve`)
- OpenAI: Check `OPENAI_API_KEY` environment variable
- Verify model name is correct for the provider

### Timeout Errors

- Increase `config.timeout_seconds`
- Reduce `config.max_iterations`
- Simplify system prompt
- Consider switching to faster model
