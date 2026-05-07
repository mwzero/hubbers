---
title: Tools Guide
---

# Tools Guide

Tools are deterministic runtime capabilities backed by Java drivers and described by `tool.yaml` manifests inside the repository.

## Current Manifest Shape

The current repo uses `tool.yaml`, not `manifest.yaml`, and tool execution is selected by the manifest `type`, not a `driver.class` field.

Representative examples:

- [hubbers-repo/src/main/resources/repo/tools/rss.fetch/tool.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/tools/rss.fetch/tool.yaml:1)
- [hubbers-repo/src/main/resources/repo/tools/file.ops/tool.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/tools/file.ops/tool.yaml:1)

## Typical Structure

```yaml
tool:
  name: rss.fetch
  version: 1.0.0
  description: Fetch and normalize RSS/Atom feed items

type: rss

config:
  default_limit: 20

input:
  schema:
    type: object
    properties:
      feeds:
        type: array
        required: true

output:
  schema:
    type: object
    properties:
      items:
        type: array
        required: true

examples:
  - name: fetch-demo
    input:
      feeds:
        - https://example.com/rss.xml
```

## Supported Runtime Concepts

The current manifests commonly include:

- `tool`
- `type`
- `config`
- `input.schema`
- `output.schema`
- `examples`
- `forms.before`

## Tool Type Dispatch

The runtime maps manifest `type` values to Java tool drivers loaded through the `ToolDriverProvider` SPI. `Bootstrap` discovers providers with `ServiceLoader`, and `hubbers-tools-builtin` contributes the default built-in set.

Tool types currently wired in the runtime:

| Type | Driver class | Description |
| --- | --- | --- |
| `http` | `HttpToolDriver` | Generic HTTP requests |
| `docker` | `DockerToolDriver` | Docker container execution |
| `rss` | `RssToolDriver` | RSS/Atom feed fetching |
| `firecrawl` | `FirecrawlToolDriver` | Web crawling via Firecrawl |
| `vector.lucene.enrich` | `LuceneVectorContextToolDriver` | Enrich items with Lucene vector context |
| `vector.lucene.upsert` | `LuceneVectorUpsertToolDriver` | Upsert vectors into Lucene index |
| `vector.lucene.search` | `LuceneVectorSearchToolDriver` | Semantic search over Lucene index |
| `lucene.kv` | `LuceneKvToolDriver` | Key-value store backed by Lucene |
| `browser.pinchtab` | `PinchtabBrowserToolDriver` | Browser automation via Pinchtab |
| `csv.write` | `CsvWriteToolDriver` | Write data to CSV files |
| `csv.read` | `CsvReadToolDriver` | Read data from CSV files |
| `file.ops` | `FileOpsToolDriver` | File system operations |
| `shell.exec` | `ShellExecToolDriver` | Shell command execution |
| `process.manage` | `ProcessManageToolDriver` | OS process management |
| `user-interaction` | `UserInputToolDriver` | Human-in-the-loop user prompts |
| `sql.query` | `SqlQueryToolDriver` | SQL database queries |
| `http.webhook` | `WebhookToolDriver` | Outbound webhook calls |

Reference:

- [Bootstrap.java](../hubbers-core/src/main/java/org/hubbers/app/Bootstrap.java)
- [BuiltinToolDriverProvider.java](../hubbers-tools-builtin/src/main/java/org/hubbers/tool/builtin/BuiltinToolDriverProvider.java)

## Forms

Many tools define `forms.before` so the web UI can collect user input before execution. This is part of the current runtime model and should be documented for any new interactive tool.

Example:

- [hubbers-repo/src/main/resources/repo/tools/browser.pinchtab/tool.yaml](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/tools/browser.pinchtab/tool.yaml:1)

## Bundled Tools

The `hubbers-repo` ships with 17 tool manifests:

| Folder | Type | Description |
| --- | --- | --- |
| `browser.pinchtab` | `browser.pinchtab` | Browser automation |
| `csv.write` | `csv.write` | Write CSV output |
| `csv_read` | `csv.read` | Read CSV input |
| `demo.calculator` | `shell.exec` | Calculator demo via shell |
| `file.ops` | `file.ops` | File read/write/delete operations |
| `http.webhook` | `http.webhook` | Outbound webhook calls |
| `lucene.kv` | `lucene.kv` | Key-value Lucene store |
| `pdf.extract` | `docker` | PDF text extraction via Docker |
| `process.manage` | `process.manage` | OS process management |
| `rss.fetch` | `rss` | Fetch and normalize RSS feeds |
| `rss.vector.context` | `vector.lucene.enrich` | Enrich RSS items with vector context |
| `shell.exec` | `shell.exec` | Shell command execution |
| `sql.query` | `sql.query` | SQL database queries |
| `user.input` | `user-interaction` | Human-in-the-loop prompts |
| `vector.lucene.search` | `vector.lucene.search` | Semantic vector search |
| `vector.lucene.upsert` | `vector.lucene.upsert` | Upsert vectors into Lucene |
| `weather.lookup` | `http` | Weather lookup via HTTP |



1. Add `hubbers-repo/src/main/resources/repo/tools/<tool-name>/tool.yaml`.
2. Choose a `type` that maps to an existing driver, or implement a new driver in `hubbers-tools-builtin` or another runtime module.
3. Register the new driver through a `ToolDriverProvider` implementation and `META-INF/services/org.hubbers.tool.ToolDriverProvider`.
4. Add input/output schemas and examples.
5. Add `forms.before` if the tool should be interactive in the web UI.

## Testing

```bash
java -jar hubbers-distribution/target/hubbers.jar list tools
java -jar hubbers-distribution/target/hubbers.jar tool run rss.fetch --input '{"feeds":["https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml"],"limit":3}'
```

## Related Docs

- [Agents Guide](AGENTS.md)
- [Pipelines Guide](Pipelines.md)
