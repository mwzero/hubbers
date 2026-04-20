---
title: Pipelines Guide
---

# Pipelines Guide

Pipelines orchestrate agents, tools, and sometimes other pipelines through declarative YAML steps.

## Current Manifest Shape

The bundled repo uses `pipeline.yaml` with a simple step model based on `id`, artifact selector, and `input_mapping`.

Representative examples:

- [hubbers-repo/src/main/resources/repo/pipelines/rss.sentiment.csv/pipeline.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/pipelines/rss.sentiment.csv/pipeline.yaml:1)
- [hubbers-repo/src/main/resources/repo/pipelines/system.report/pipeline.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/pipelines/system.report/pipeline.yaml:1)

## Typical Structure

```yaml
pipeline:
  name: rss.sentiment.csv
  version: 1.0.0
  description: Fetch RSS, run analysis, and persist output

steps:
  - id: fetch
    tool: rss.fetch

  - id: analyze
    agent: research.assistant
    input_mapping:
      query: ${steps.fetch.output.items}

  - id: write
    tool: file.ops
    input_mapping:
      operation: write
      path: ${report_path}
      content: ${steps.analyze.output.answer}

examples:
  - name: demo
    input:
      report_path: ./output/report.txt
```

## Current Step Selectors

The current repo shows these step forms:

- `tool: <name>`
- `agent: <name>`

The runtime documentation and class design also support pipeline execution as a first-class type, but examples in the bundled repo are primarily tool and agent compositions.

## Input Mapping

Current pipeline manifests use variable interpolation patterns such as:

- `${steps.fetch.output.items}`
- `${csv_output_path}`
- `${report_path}`

This keeps pipeline logic declarative and avoids custom code for straightforward orchestration.

## Good Use Cases

Pipelines fit well when you need:

- repeatable multi-step workflows
- fixed orchestration across artifacts
- data movement between tool and agent outputs
- operational reports or import/export flows

## Current Examples In The Repo

- RSS ingestion and enrichment flows
- CSV import and vector indexing
- file backup and report generation
- browser-assisted scraping

## Testing

```bash
java -jar hubbers-distribution/target/hubbers.jar list pipelines
java -jar hubbers-distribution/target/hubbers.jar pipeline run system.report --input '{"disk_command":"df -h","report_path":"./output/system-report.txt"}'
```

## Related Docs

- [Tools Guide](Tools.md)
- [Agents Guide](AGENTS.md)
