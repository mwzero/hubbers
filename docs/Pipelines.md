# Pipeline Development Guide

Documentation for creating and modifying pipelines in the Hubbers framework.

## What Are Pipelines?

Pipelines are **orchestration workflows** that chain together agents, tools, and other pipelines to accomplish complex tasks:
- Multi-step data processing (fetch → process → store)
- Agent collaboration (agent1 output → agent2 input)
- Conditional branching (if/else logic)
- Error handling and retries

Pipelines define **declarative workflows** using YAML, enabling composition without code changes.

## Pipeline Format

Pipelines use YAML format (`pipeline.yaml`):

```yaml
pipeline:
  name: my-pipeline
  version: 1.0.0
  description: What this pipeline does

input:
  schema:
    type: object
    properties:
      query: {type: string, required: true}

output:
  schema:
    type: object
    properties:
      result: {type: object}

steps:
  - name: step1
    type: tool|agent|pipeline
    artifact: artifact-name
    input:
      field: "${input.query}"
    output_key: step1_result

  - name: step2
    type: agent
    artifact: processor
    input:
      data: "${step1_result.output}"
    output_key: final_result

final_output:
  result: "${final_result}"
```

## Field Reference

### Required Fields

#### `pipeline`
Metadata:
- `name` (string): Unique identifier, kebab-case
- `version` (string): Semantic version
- `description` (string): Human-readable description

#### `input` / `output`
JSON Schema definitions (same as agents/tools).

#### `steps`
Array of step definitions:
- `name` (string): Step identifier (alphanumeric, hyphens, underscores)
- `type` (string): "tool", "agent", or "pipeline"
- `artifact` (string): Name of the tool/agent/pipeline to invoke
- `input` (object): Input mapping (supports variable interpolation)
- `output_key` (string): Variable name to store the result

#### `final_output`
Output mapping using step results.

### Optional Fields

#### `variables`
Pipeline-scoped variables:
```yaml
variables:
  max_items: 10
  output_format: "json"
```

#### `error_handling`
Global error handling strategy:
```yaml
error_handling:
  on_error: continue|stop
  retry_count: 3
  retry_delay_ms: 1000
```

## Variable Interpolation

Pipelines support variable substitution using `${}` syntax:

```yaml
steps:
  - name: fetch
    type: tool
    artifact: rss.fetch
    input:
      url: "${input.feed_url}"
      max_items: "${variables.max_items}"
    output_key: articles

  - name: process
    type: agent
    artifact: text.summarizer
    input:
      text: "${articles.items[0].description}"
    output_key: summary

final_output:
  articles: "${articles}"
  first_summary: "${summary.result}"
```

**Supported sources:**
- `${input.field}` - Pipeline input
- `${variables.name}` - Pipeline variables
- `${step_name.field}` - Previous step output
- `${step_name.nested.field}` - Nested field access
- `${step_name.array[0]}` - Array indexing

## Pipeline Patterns

### Pattern 1: Linear Pipeline

**Use case**: Sequential processing (A → B → C)
**Examples**: rss.csv, pdf.summary

```yaml
pipeline:
  name: linear-pipeline
  version: 1.0.0
  description: Sequential steps

input:
  schema:
    type: object
    properties:
      input_data: {type: string}

output:
  schema:
    type: object
    properties:
      result: {type: object}

steps:
  - name: step1
    type: tool
    artifact: tool1
    input:
      data: "${input.input_data}"
    output_key: intermediate1

  - name: step2
    type: agent
    artifact: agent1
    input:
      data: "${intermediate1}"
    output_key: intermediate2

  - name: step3
    type: tool
    artifact: tool2
    input:
      data: "${intermediate2.result}"
    output_key: final

final_output:
  result: "${final}"
```

### Pattern 2: Fan-Out Pipeline

**Use case**: Process multiple items in parallel concept
**Examples**: rss.sentiment.csv (fetch → sentiment per article)

```yaml
pipeline:
  name: fan-out-pipeline
  version: 1.0.0
  description: Process items individually

steps:
  - name: fetch_items
    type: tool
    artifact: data.source
    input:
      query: "${input.query}"
    output_key: items

  - name: process_each
    type: agent
    artifact: item.processor
    input:
      items: "${items.results}"  # Pass array
      # Agent handles iteration internally
    output_key: processed

final_output:
  results: "${processed}"
```

### Pattern 3: Agent Orchestration

**Use case**: Combine specialized agents
**Examples**: rss.sentiment.ner.translate

```yaml
pipeline:
  name: multi-agent-pipeline
  version: 1.0.0
  description: Chain multiple agents

steps:
  - name: sentiment
    type: agent
    artifact: rss.sentiment
    input:
      items: "${input.articles}"
    output_key: with_sentiment

  - name: ner
    type: agent
    artifact: rss.ner
    input:
      items: "${with_sentiment.items}"
    output_key: with_entities

  - name: translate
    type: agent
    artifact: rss.translate
    input:
      items: "${with_entities.items}"
      target_language: "en"
    output_key: translated

final_output:
  results: "${translated.items}"
```

### Pattern 4: Conditional Pipeline

**Use case**: Branching logic based on conditions
**Currently**: Implemented via agent logic or pipeline composition

```yaml
pipeline:
  name: conditional-pipeline
  version: 1.0.0
  description: Conditional execution

steps:
  - name: check
    type: agent
    artifact: condition.checker
    input:
      data: "${input.data}"
    output_key: check_result

  # Agent decides which path to take
  - name: route
    type: agent
    artifact: router.agent
    input:
      condition: "${check_result.condition}"
      data: "${input.data}"
    output_key: routed

  - name: process
    type: agent
    artifact: processor
    input:
      data: "${routed.data}"
    output_key: final

final_output:
  result: "${final}"
```

## Best Practices

### Step Naming

1. **Use descriptive names**: `fetch_articles`, not `step1`
2. **Follow naming convention**: snake_case for step names
3. **Avoid reserved words**: Don't use `input`, `output`, `variables`

### Input Mapping

1. **Validate mappings**: Ensure source fields exist
2. **Type compatibility**: Match source/target types
3. **Default values**: Use pipeline variables for defaults
4. **Error handling**: Consider missing fields

### Output Mapping

1. **Clear final output**: Map only necessary fields
2. **Preserve metadata**: Include step info if needed
3. **Flatten when possible**: Avoid deep nesting
4. **Document structure**: Add comments for complex mappings

### Error Handling

1. **Graceful degradation**: Continue on non-critical failures
2. **Retry transient errors**: Network, API rate limits
3. **Log context**: Include step name, input, error
4. **Return partial results**: Don't lose all data on single failure

### Performance

1. **Minimize steps**: Combine operations when possible
2. **Avoid redundant calls**: Cache results if reused
3. **Parallel concept**: Design for eventual parallel execution
4. **Resource limits**: Consider memory for large datasets

## Pipeline Examples

### rss.csv

**Purpose**: Fetch RSS feed → Save to CSV

```yaml
pipeline:
  name: rss.csv
  version: 1.0.0
  description: Fetch RSS feed and export to CSV

input:
  schema:
    type: object
    properties:
      url: {type: string, required: true}
      output_file: {type: string, default: "rss.csv"}

output:
  schema:
    type: object
    properties:
      file_path: {type: string}
      item_count: {type: number}

steps:
  - name: fetch_rss
    type: tool
    artifact: rss.fetch
    input:
      url: "${input.url}"
      max_items: 50
    output_key: rss_items

  - name: write_csv
    type: tool
    artifact: csv.write
    input:
      file_path: "${input.output_file}"
      headers: ["title", "link", "description", "published_date"]
      rows: "${rss_items.items}"
    output_key: csv_result

final_output:
  file_path: "${csv_result.file_path}"
  item_count: "${rss_items.item_count}"
```

### rss.sentiment.ner.translate

**Purpose**: RSS → Sentiment → NER → Translation

```yaml
pipeline:
  name: rss.sentiment.ner.translate
  version: 1.0.0
  description: Multi-stage RSS processing

input:
  schema:
    type: object
    properties:
      url: {type: string, required: true}
      target_language: {type: string, default: "en"}

output:
  schema:
    type: object
    properties:
      items: {type: array}

steps:
  - name: fetch
    type: tool
    artifact: rss.fetch
    input:
      url: "${input.url}"
    output_key: articles

  - name: sentiment_analysis
    type: agent
    artifact: rss.sentiment
    input:
      items: "${articles.items}"
    output_key: with_sentiment

  - name: named_entity_recognition
    type: agent
    artifact: rss.ner
    input:
      items: "${with_sentiment.items}"
    output_key: with_entities

  - name: translation
    type: agent
    artifact: rss.translate
    input:
      items: "${with_entities.items}"
      target_language: "${input.target_language}"
    output_key: translated

final_output:
  items: "${translated.items}"
```

### pdf.summary

**Purpose**: Extract text from PDF → Summarize

```yaml
pipeline:
  name: pdf.summary
  version: 1.0.0
  description: Extract and summarize PDF

input:
  schema:
    type: object
    properties:
      pdf_path: {type: string, required: true}

output:
  schema:
    type: object
    properties:
      summary: {type: string}

steps:
  - name: extract_text
    type: tool
    artifact: pdf.extract
    input:
      file_path: "${input.pdf_path}"
    output_key: extracted

  - name: summarize
    type: agent
    artifact: text.summarizer
    input:
      text: "${extracted.text}"
      max_length: 500
    output_key: summary

final_output:
  summary: "${summary.result}"
```

## Testing Pipelines

### Manual Testing

```bash
# Test with CLI
hubbers pipeline run my-pipeline --input '{"field":"value"}'

# Test with file
echo '{"field":"value"}' > input.json
hubbers pipeline run my-pipeline --input input.json

# Check execution logs
ls repo/executions/
cat repo/executions/<execution-id>/execution-log.txt
```

### Debugging

1. **Check execution logs**: `repo/executions/<id>/execution-log.txt`
2. **Inspect step outputs**: `execution-metadata.json` contains all step results
3. **Validate variable mappings**: Ensure `${}` references are correct
4. **Test steps individually**: Run agent/tool directly to isolate issues

### Validation Testing

Input/output schemas are validated automatically:
- Invalid pipeline input → Execution fails
- Invalid step input → Step fails, pipeline may continue
- Invalid step output → Logged as warning
- Invalid pipeline output → Execution fails

## Troubleshooting

### Variable Substitution Errors

```
Error: Cannot resolve variable: ${step1.missing_field}
```

**Solutions:**
- Check step output structure: `cat repo/executions/<id>/execution-metadata.json`
- Verify step name matches `output_key`
- Ensure field exists in step output
- Use correct syntax: `${step.field}`, not `$step.field`

### Step Execution Failures

```
Step 'process' failed: Agent returned error
```

**Solutions:**
- Run agent/tool directly: `hubbers agent run <name> --input '...'`
- Check agent/tool logs
- Validate input schema matches expected format
- Review step input mapping

### Type Mismatch Errors

```
Schema validation failed: Expected string, got object
```

**Solutions:**
- Check variable interpolation: Are you passing whole object instead of field?
- Use correct accessor: `${step.field}` vs `${step}`
- Review schema definitions in agent/tool manifests

### Missing Artifact Errors

```
Artifact not found: unknown-agent
```

**Solutions:**
- Verify artifact name matches directory name
- Run `hubbers list agents/tools/pipelines`
- Check artifact manifest exists and is valid
- Rebuild if using native image

## Advanced Topics

### Nested Pipeline Composition

Pipelines can invoke other pipelines:

```yaml
steps:
  - name: sub_workflow
    type: pipeline
    artifact: nested.pipeline
    input:
      data: "${input.data}"
    output_key: sub_result

  - name: final_step
    type: agent
    artifact: finalizer
    input:
      data: "${sub_result}"
    output_key: result
```

### Dynamic Tool Selection

Use agent to decide which tool to call:

```yaml
steps:
  - name: routing
    type: agent
    artifact: router.agent
    input:
      request: "${input.request}"
    output_key: route

  # Agent's output includes tool name and parameters
  - name: execution
    type: tool
    artifact: "${route.selected_tool}"  # Dynamic
    input: "${route.parameters}"
    output_key: result
```

**Note**: Dynamic artifact selection may require custom executor logic.

### State Management

For stateful workflows, use file system or vector DB:

```yaml
steps:
  - name: save_state
    type: tool
    artifact: file.ops
    input:
      operation: write
      path: "state/${input.session_id}.json"
      content: "${step1_result}"

  - name: load_state
    type: tool
    artifact: file.ops
    input:
      operation: read
      path: "state/${input.session_id}.json"
```

## Related Documentation

- [Agent Development Guide](../agents/AGENTS.md) - Creating agents for pipelines
- [Tool Development Guide](../tools/AGENTS.md) - Creating tools for pipelines
- Root AGENTS.md - Project setup and architecture
