---
title: MCP Server
---

# MCP Server

Hubbers ships a [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that exposes all repository artifacts as MCP tools and prompts. External chat UIs such as Claude Desktop and VS Code can connect to it and invoke Hubbers tools and agents directly.

## Module

MCP support lives in the `hubbers-mcp` module:

- `McpRequestHandler` — JSON-RPC 2.0 dispatcher
- `McpToolProvider` — converts Hubbers tool manifests into MCP tool definitions
- `McpPromptProvider` — converts Hubbers agent manifests into MCP prompt definitions
- `McpResourceProvider` — exposes repository resources to MCP clients
- `McpStdioTransport` — reads from stdin / writes to stdout (for Claude Desktop)
- `McpSseTransport` — Server-Sent Events transport available through the web server

## Transports

### Stdio (CLI)

Start the MCP server reading from stdin and writing to stdout:

```bash
java -jar hubbers-distribution/target/hubbers.jar mcp
```

All logging is redirected to stderr so stdout stays clean for JSON-RPC messages.

### SSE (Web)

When the web server is running the MCP endpoint is available at:

- `POST /mcp` — JSON-RPC request/response
- `GET /mcp/sse` — SSE stream for MCP clients that require it

## Supported MCP Methods

| Method | Description |
| --- | --- |
| `initialize` | Capability negotiation |
| `notifications/initialized` | Client acknowledgement (no response) |
| `tools/list` | List all Hubbers tools |
| `tools/call` | Execute a Hubbers tool, pipeline, or agent |
| `prompts/list` | List all Hubbers agents as MCP prompts |
| `prompts/get` | Retrieve a specific agent prompt |

## Tool Namespacing

The server exposes three prefixes so MCP clients can distinguish artifact types:

| Prefix | Maps to |
| --- | --- |
| `tool.<name>` | Hubbers tool |
| `pipeline.<name>` | Hubbers pipeline |
| `agent.<name>` | Hubbers agent |

## Connecting Claude Desktop

Run `hubbers mcp --config` to print the snippet, or add it manually to your Claude Desktop config:

```json
{
  "mcpServers": {
    "hubbers": {
      "command": "java",
      "args": ["-jar", "/path/to/hubbers.jar", "mcp"]
    }
  }
}
```

## Protocol Version

The server negotiates MCP protocol version `2025-03-26`.

## Related Docs

- [Software Architecture](SWA.md)
- [Agentic Architecture](AGENTIC_ARCHITECTURE.md)
- [Tools Guide](Tools.md)
