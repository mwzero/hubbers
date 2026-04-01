# Agentic Architecture - Nomenclature Guide

## Overview

Il framework Hubbers ora supporta **agenti autonomi** che possono chiamare tools/task e mantenere memoria conversazionale. Per evitare confusione tra i diversi concetti, questa guida chiarisce la nomenclatura.

---

## Nomenclature Distinctions

### 1. **Tool / Task** (Framework Level)
**Cosa Sono:** Capacità eseguibili del framework Hubbers (HTTP, RSS, CSV, File Ops, Shell, etc.)

**Dove Vivono:**
- `ToolDriver` interface + implementations (e.g., `RssToolDriver`, `FileOpsToolDriver`)
- `ToolManifest` YAML files in `repo/tools/*/tool.yaml`
- `ToolExecutor` gestisce il registry e l'esecuzione

**Esempio:**
```java
public class RssToolDriver implements ToolDriver {
    public String type() { return "rss"; }
    public JsonNode execute(ToolManifest manifest, JsonNode input) { ... }
}
```

```yaml
# repo/tools/rss.fetch/tool.yaml
tool:
  name: rss.fetch
  description: Fetch and normalize RSS/Atom feed items
type: rss
```

**Named "Tool" in codebase because:** Hubbers is a framework for executing AI agents and tools/tasks.

---

### 2. **FunctionDefinition** (LLM Interface Level)
**Cosa Sono:** Descrizioni di tool/task in formato JSON Schema per LLM function calling

**Dove Vivono:**
- `FunctionDefinition` model class
- Generato da `ToolManifest` tramite `ToolToFunctionConverter`
- Field `functions` in `ModelRequest`

**Purpose:** Comunicare all'LLM quali tool/task sono disponibili.

**Esempio:**
```java
// Generated from ToolManifest
FunctionDefinition function = new FunctionDefinition(
    "rss.fetch",  // name from ToolManifest
    "Fetch and parse RSS/Atom feeds",  // description
    parametersSchema  // JSON Schema from ToolManifest.input
);
```

**Named "Function" because:** OpenAI API calls them "functions" in function calling feature.

---

### 3. **FunctionCall** (LLM Response Level)
**Cosa Sono:** Richieste dell'LLM di eseguire un tool/task specifico

**Dove Vivono:**
- `FunctionCall` model class
- Field `functionCalls` in `ModelResponse`
- Restituito dall'LLM quando decide di chiamare un tool

**Purpose:** L'LLM comunica "voglio eseguire questo tool con questi parametri".

**Esempio:**
```java
// Returned by LLM in ModelResponse
FunctionCall call = new FunctionCall(
    "call_abc123",  // ID
    "rss.fetch",    // tool name to execute
    {               // arguments (JSON)
        "feeds": ["https://example.com/rss"],
        "limit": 10
    }
);
```

**Named "FunctionCall" because:** It's the LLM requesting to call a function (tool).

---

## Data Flow

```
1. Agent Configuration (agent.yaml)
   ↓
   config.tools: ["rss.fetch", "file.ops"]  ← Tool/Task names

2. AgenticExecutor Initialization
   ↓
   Load ToolManifests from repo/tools/
   ↓
   Convert to FunctionDefinitions via ToolToFunctionConverter
   ↓
   FunctionDefinition[] → ModelRequest.functions

3. LLM Processing
   ↓
   Receives available functions
   ↓
   Decides to call a function
   ↓
   Returns FunctionCall[] in ModelResponse.functionCalls

4. AgenticExecutor ReAct Loop
   ↓
   For each FunctionCall:
     - Load ToolManifest by function name
     - Execute via ToolExecutor.execute(ToolManifest, arguments)
     - Get RunResult
     - Add result to conversation history
   ↓
   Continue until final answer or max iterations
```

---

## Code Examples

### Defining an Agentic Agent

```yaml
# repo/agents/research.assistant/agent.yaml
agent:
  name: research.assistant

config:
  # Specify which tools/tasks this agent can call
  tools:
    - rss.fetch     # References ToolManifest in repo/tools/rss.fetch/
    - file.ops      # References ToolManifest in repo/tools/file.ops/
    - csv.read      # References ToolManifest in repo/tools/csv.read/
  
  max_iterations: 10
  timeout_seconds: 60
```

### Executing an Agentic Agent

```java
AgenticExecutor executor = ...;
AgentManifest manifest = repository.loadAgent("research.assistant");

JsonNode input = mapper.createObjectNode()
    .put("query", "What are latest AI developments?");

// Execute with ReAct loop
RunResult result = executor.execute(manifest, input, null);
```

### Inside AgenticExecutor

```java
// 1. Convert ToolManifests to FunctionDefinitions
List<String> toolNames = manifest.getConfig().get("tools");
List<FunctionDefinition> functions = toolNames.stream()
    .map(name -> repository.loadTool(name))
    .map(toolManifest -> functionConverter.convert(toolManifest))
    .collect(Collectors.toList());

// 2. Send to LLM
ModelRequest request = new ModelRequest();
request.setFunctions(functions);  // Available tools as functions

ModelResponse response = llm.generate(request);

// 3. Execute function calls (tool invocations)
for (FunctionCall call : response.getFunctionCalls()) {
    ToolManifest toolManifest = repository.loadTool(call.getName());
    RunResult toolResult = toolExecutor.execute(toolManifest, call.getArguments());
    // Add result to conversation and continue
}
```

---

## Summary Table

| Concept | Level | Class | Purpose |
|---------|-------|-------|---------|
| **Tool/Task** | Framework | `ToolDriver`, `ToolManifest` | Executable capabilities (RSS, CSV, HTTP, etc.) |
| **FunctionDefinition** | LLM Interface | `FunctionDefinition` | Describes tool to LLM in JSON Schema format |
| **FunctionCall** | LLM Response | `FunctionCall` | LLM's request to execute a specific tool |
| **AgenticExecutor** | Orchestration | `AgenticExecutor` | ReAct loop coordinator (calls LLM + executes tools) |
| **ConversationMemory** | State | `ConversationMemory` | Persists conversation history and facts |

---

## Key Insight

**Tools are tasks** in Hubbers framework (what the system can DO).  
**Functions are tools** as exposed to LLM (what the LLM can CALL).  
**Function calls are tool invocations** requested by LLM (what the LLM WANTS to do).

This three-layer separation provides:
1. **Abstraction:** Framework tools independent of LLM API format
2. **Flexibility:** Same tool can be called by agents, pipelines, or CLI
3. **Clarity:** Distinct names for distinct concepts
