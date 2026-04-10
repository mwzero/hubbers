# Migration Summary: Task Command Unification

**Date**: January 2025  
**Version**: 0.1.0-SNAPSHOT  
**Status**: ✅ COMPLETED

## Overview

Unified agent and task execution under a single `agent run` command with intelligent routing between direct execution and natural language processing.

## Breaking Changes

### ❌ REMOVED: `hubbers task run` command

The standalone `task run` command has been removed. All task execution now flows through `agent run` with the `--request` flag.

**Before:**
```bash
# Old way (no longer works)
hubbers task run --request "Fetch RSS from TechCrunch"
hubbers task run --request "Analyze sentiment" --context '{"text":"..."}'
```

**After:**
```bash
# New unified way
hubbers agent run universal.task --request "Fetch RSS from TechCrunch"
hubbers agent run universal.task --request "Analyze sentiment" --context '{"text":"..."}'
```

### 🔄 CHANGED: Agent execution modes

The `agent run` command now supports **two execution modes**:

1. **Direct mode** (default): JSON input → agent execution
   ```bash
   hubbers agent run demo.search --input '{"query":"test"}'
   ```

2. **Natural language mode**: Text request → universal.task agent → tool orchestration
   ```bash
   hubbers agent run universal.task --request "Fetch RSS from TechCrunch"
   ```

## Architecture Changes

### RuntimeFacade Intelligent Routing

Added automatic detection of natural language requests in `runAgent()`:

```java
public RunResult runAgent(String name, JsonNode input, String conversationId) {
    if (isNaturalLanguageRequest(input)) {
        return executeNaturalLanguageTask(name, input, conversationId);
    }
    // Standard agent execution...
}

private boolean isNaturalLanguageRequest(JsonNode input) {
    return input.has("request") && input.get("request").isTextual();
}
```

**Detection pattern**: `{"request": "..."}`

When detected, lazily initializes `NaturalLanguageTaskService` and routes execution through the universal.task agent with full tool catalog injection.

### NaturalLanguageTaskService Visibility

Changed from `public` to **package-private**:
- Only accessible via `RuntimeFacade`
- No longer instantiated directly by CLI or Web code
- Lazy initialization on first natural language request

**bootstrap changes:**
- ❌ Removed `Bootstrap.createNaturalLanguageTaskService()`
- ✅ Service now created internally by RuntimeFacade when needed

### CLI Command Simplification

**HubbersCommand.java**:
- ❌ Removed entire `TaskCommand` class (195 lines)
- ✅ Enhanced `AgentRun` with new options:
  - `--request` (natural language)
  - `--context` (JSON data)
  - `--conversation` (conversation ID)
  - `--verbose` (detailed output)

**Example usage:**
```bash
# Direct mode
hubbers agent run demo.search --input '{"query":"test"}'

# Natural language mode
hubbers agent run universal.task --request "Fetch RSS from TechCrunch"

# With context
hubbers agent run universal.task \
  --request "Analyze sentiment" \
  --context '{"text":"Great product!"}'

# Continue conversation
hubbers agent run universal.task \
  --request "Now translate to Italian" \
  --conversation abc-123-def
```

### Web API Consistency

**WebServer.java** and **WebMain.java**:
- ❌ Removed `NaturalLanguageTaskService` dependency injection
- ✅ All endpoints now use `RuntimeFacade.runAgent()`
- ⚠️ **API endpoints unchanged** (backward compatible)

**Endpoints still work identically:**
```bash
POST /api/task/execute
{
  "request": "Fetch RSS from TechCrunch",
  "context": null
}
```

Backend now routes through `RuntimeFacade.runAgent("universal.task", input, null)` with automatic natural language detection.

## Migration Guide

### For CLI Users

Replace all `task run` commands with `agent run universal.task`:

```bash
# Before
hubbers task run --request "Summarize this text"

# After
hubbers agent run universal.task --request "Summarize this text"
```

### For API Clients

**No changes required**. Web API endpoints remain compatible:
- POST `/api/task/execute`
- POST `/api/task/continue`
- GET `/api/task/info`

### For Developers

If you were using `NaturalLanguageTaskService` directly:

**Before:**
```java
NaturalLanguageTaskService taskService = Bootstrap.createNaturalLanguageTaskService(facade);
TaskExecutionResult result = taskService.executeTask("request", context);
```

**After:**
```java
ObjectMapper mapper = new ObjectMapper();
ObjectNode input = mapper.createObjectNode();
input.put("request", "request");
if (context != null) input.set("context", context);

RunResult result = runtimeFacade.runAgent("universal.task", input, null);
```

## Benefits

1. **Simplified API**: One command for all agent execution
2. **Automatic routing**: No need to choose between agent/task
3. **Consistent behavior**: CLI and Web use same code path
4. **Better encapsulation**: NaturalLanguageTaskService hidden as implementation detail
5. **Conversation support**: Built into unified command
6. **Reduced duplication**: Removed 195 lines of duplicate command code

## Implementation Details

### Files Modified

**Core execution:**
- `RuntimeFacade.java` - Added intelligent routing
- `NaturalLanguageTaskService.java` - Made package-private
- `Bootstrap.java` - Removed public factory method

**CLI:**
- `HubbersCommand.java` - Removed TaskCommand, enhanced AgentRun

**Web:**
- `WebMain.java` - Removed taskService injection
- `WebServer.java` - Routes through RuntimeFacade

**Documentation:**
- `AGENTS.md` - Updated testing commands
- `MIGRATION_SUMMARY.md` - This document

### Files Removed

None (only code sections removed within existing files)

### New Artifacts Created

During agent/skill migration:
- `repo/skills/sentiment-analysis/SKILL.md`
- `repo/skills/ner-extraction/SKILL.md`
- `repo/skills/translation/SKILL.md`
- `repo/agents/skill.executor/AGENT.md`
- `repo/agents/demo.search/AGENT.md`
- `repo/agents/research.assistant/AGENT.md`
- `repo/agents/universal.task/AGENT.md`

## Testing

**Build verification:**
```bash
mvn clean package -DskipTests
```

**CLI testing:**
```bash
# Natural language via universal.task
hubbers agent run universal.task --request "Fetch RSS from TechCrunch"

# Direct JSON execution
hubbers agent run demo.search --input '{"query":"test"}'

# Conversation flow
hubbers agent run universal.task --request "Fetch RSS" --conversation abc-123
hubbers agent run universal.task --request "Analyze sentiment" --conversation abc-123

# Verify old command removed
hubbers task run --help  # Should show error: command not found
```

**Web testing:**
```bash
# Start server
hubbers web --port 7070

# Test endpoint (curl)
curl -X POST http://localhost:7070/api/task/execute \
  -H "Content-Type: application/json" \
  -d '{"request":"Fetch RSS from TechCrunch"}'
```

## Rollback Plan

If rollback is needed:

1. Revert commits in this order:
   - Documentation changes
   - Web changes
   - CLI TaskCommand removal
   - RuntimeFacade routing
   - NaturalLanguageTaskService visibility

2. Restore `Bootstrap.createNaturalLanguageTaskService()`

3. Restore TaskCommand in `HubbersCommand.java`

Git commits should be atomic per component for easy revert.

## Future Improvements

1. **Alias support**: `hubbers run <request>` shorthand
2. **Smart default**: Auto-detect universal.task when --request used
3. **Context inference**: Auto-extract context from conversation history
4. **Streaming output**: Real-time token streaming for verbose mode
5. **Agent recommendations**: Suggest specific agents based on request analysis

## Questions?

See:
- [AGENTS.md](../AGENTS.md) - Updated testing commands
- [AGENTIC_ARCHITECTURE.md](AGENTIC_ARCHITECTURE.md) - Execution architecture
- [repo/agents/universal.task/AGENT.md](../repo/agents/universal.task/AGENT.md) - Universal task agent definition
