# AGENTS.md

AI coding agent documentation for the Hubbers project.

## Project Overview

**Hubbers** is a Git-native Java framework for executing AI agents, tools, pipelines, and skills defined as YAML/Markdown artifacts. The project transforms a repository into an executable agent runtime.

**Key Architecture:**
- **RuntimeFacade**: Central execution entry point
- **AgenticExecutor**: ReAct loop with function calling
- **ArtifactRepository**: Manifest loading from `hubbers-repo/src/main/resources/repo/`
- **Four artifact types**: Agents, Tools, Pipelines, Skills

## Setup Commands

```bash
# Install dependencies
mvn clean install

# Build JAR
mvn clean package

# Build native executable (GraalVM required)
./build-native.sh       # Linux/macOS
build-native.bat        # Windows

# Run tests
mvn test

# Install globally
./install.sh            # Linux/macOS
install.bat             # Windows (as Administrator)

# Set API key
export OPENAI_API_KEY=your_key_here
```

## Code Style

- **Java 21 features**: Records, switch expressions, text blocks
- **Lombok**: Use `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor` for boilerplate reduction
- **Functional patterns**: Prefer streams over loops, use `Optional` for null safety
- **Logging**: SLF4J with Logback (`log.debug()`, `log.info()`, `log.error()`)
- **JSON/YAML**: Jackson with `ObjectMapper` (use `JacksonFactory` for instances)
- **No Spring**: Lightweight design, use pure Java dependency injection

**Naming conventions:**
- Classes: PascalCase (`AgentExecutor`, `ToolDriver`)
- Methods: camelCase (`execute()`, `loadAgent()`)
- Constants: UPPER_SNAKE_CASE (`FRONTMATTER_DELIMITER`)
- Packages: lowercase (`org.hubbers.agent`)

## Testing Instructions

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AgentExecutorTest

# Run with specific pattern
mvn test -Dtest=*Agent*

# Manual testing of artifacts
hubbers agent run <name> --input '{"key":"value"}'
hubbers tool run <name> --input '{"key":"value"}'
hubbers pipeline run <name> --input '{"key":"value"}'
hubbers skill run <name> --input '{"key":"value"}'

# Natural language execution (uses universal.task agent)
hubbers agent run universal.task --request "Fetch RSS from TechCrunch"
hubbers agent run universal.task --request "Analyze sentiment" --context '{"text":"Great product!"}'
hubbers agent run universal.task --request "Continue analysis" --conversation <conv-id>

# Agent-specific options
# --request      Execute as natural language task (auto-routes to NaturalLanguageTaskService)
# --context      Provide additional data as JSON for natural language tasks
# --conversation Resume a multi-turn conversation by ID
# --verbose      Show detailed execution trace

# Validate artifacts
hubbers skill validate <name>
```

**Test structure:**
- Unit tests: `src/test/java/org/hubbers/`
- Follow existing patterns in `AgentExecutorTest.java`
- Use JUnit 5 annotations: `@Test`, `@BeforeEach`
- Mock with manual implementations (no Mockito)

## Development Workflows

### Creating a New Agent

**Important**: Only create agents for truly agentic workflows:
- Complex tasks requiring ReAct loop and tool calling
- Form-based user interaction
- Multi-step orchestration with autonomous decision-making

For simple prompt-based operations, create a **skill** instead (see `hubbers-repo/src/main/resources/repo/skills/AGENTS.md`).

Create an `AGENT.md` file with YAML frontmatter + Markdown body:

```bash
cd hubbers-repo/src/main/resources/repo/agents
mkdir my-agent
cd my-agent
# Create AGENT.md with YAML frontmatter + Markdown body
```

**Required fields:**
- `agent`: name, version, description
- `model`: provider, name, temperature
- `input`: JSON Schema for input validation
- `output`: JSON Schema for output validation
- `## Instructions` section in Markdown body for the system prompt

See `hubbers-repo/src/main/resources/repo/agents/AGENTS.md` for detailed agent creation guide.

### Creating a New Skill

For simple prompt-based methodologies (sentiment analysis, translation, summarization):

```bash
cd hubbers-repo/src/main/resources/repo/skills
mkdir my-skill
cd my-skill
# Create SKILL.md with YAML frontmatter + instructions
```

**When to use skills instead of agents:**
- Pure prompt-based operations without tool calling
- Reusable methodologies across multiple agents
- Data transformation tasks
- Simple classification or extraction

See `hubbers-repo/src/main/resources/repo/skills/AGENTS.md` for detailed skill creation guide.

**Migration note**: Former simple agents (text.summarizer, rss.sentiment, rss.ner, rss.translate) are now skills. Use the `skill.executor` agent to invoke skills in pipelines.

### Adding a New Tool

```bash
cd hubbers-repo/src/main/resources/repo/tools
mkdir my-tool
cd my-tool
# Create tool.yaml with type and config
```

Implement `ToolDriver` interface in Java:
```java
public class MyToolDriver implements ToolDriver {
    public RunResult execute(ToolManifest manifest, JsonNode input) {
        // Implementation
    }
}
```

Register in `Bootstrap.java`.

### Building a Pipeline

```bash
cd hubbers-repo/src/main/resources/repo/pipelines
mkdir my-pipeline
cd my-pipeline
# Create pipeline.yaml with steps
```

Define sequential steps that call agents/tools/pipelines.

### Implementing a Skill

```bash
cd hubbers-repo/src/main/resources/repo/skills
mkdir my-skill
cd my-skill
# Create SKILL.md with YAML frontmatter
mkdir scripts  # Optional: executable scripts
```

Follow agentskills.io specification (see `hubbers-repo/src/main/resources/repo/skills/AGENTS.md`).

## Architecture Notes

**Execution Flow:**
1. CLI/Web → `RuntimeFacade`
2. `RuntimeFacade` → Load manifest from `ArtifactRepository`
3. Dispatch to appropriate executor:
   - `AgentExecutor` (single-shot)
   - `AgenticExecutor` (ReAct loop with tools)
   - `ToolExecutor` (driver dispatch)
   - `PipelineExecutor` (orchestration)
   - `SkillExecutor` (hybrid execution)
4. Validate input/output against schemas
5. Return `RunResult` with status/output/error

**Key Classes:**
- `Bootstrap.createRuntimeFacade()`: Initialization
- `ArtifactRepository`: Manifest loading
- `ArtifactCatalogInjector`: Dynamic tool injection
- `ArtifactToFunctionConverter`: Convert artifacts to LLM functions
- `AgenticExecutor`: ReAct loop implementation

**Agent vs Skill Architecture:**
- **Agents**: Autonomous executors with model config, can use ReAct loop and tools
  - Examples: `research.assistant`, `universal.task`, `demo.search`
  - Invoked directly: `hubbers agent run <name>`
- **Skills**: Reusable methodologies/instructions without execution config
  - Examples: `sentiment-analysis`, `ner-extraction`, `translation`, `text-summarizer`
  - Invoked via: `skill.executor` agent or injected into prompts
  - Follow [agentskills.io](https://agentskills.io) specification

**Progressive Disclosure:**
- Skills use lightweight metadata caching
- Full manifests loaded on demand
- Reduces memory footprint for large repositories

**See also:**
- [docs/SWA.md](docs/SWA.md) - Software architecture
- [docs/AGENTIC_ARCHITECTURE.md](docs/AGENTIC_ARCHITECTURE.md) - Agent execution patterns
- [docs/NATIVE_BUILD.md](docs/NATIVE_BUILD.md) - GraalVM native compilation

## Common Issues

### GraalVM Native Build Errors

**Problem**: Reflection configuration missing
**Solution**: Add classes to `reflect-config.json` in `hubbers-framework/src/main/resources/META-INF/native-image/`

```json
{
  "name": "com.example.MyClass",
  "allDeclaredConstructors": true,
  "allDeclaredFields": true,
  "allDeclaredMethods": true
}
```

### Tool Driver Not Found

**Problem**: Tool registered but not found at runtime
**Solution**: Check `Bootstrap.java` - ensure driver is registered:

```java
toolExecutor.registerDriver("my.tool", new MyToolDriver());
```

### Model Provider Configuration

**Problem**: "No model provider available"
**Solution**: Check `application.yaml`:

```yaml
openai:
  apiKey: ${OPENAI_API_KEY}
  baseUrl: https://api.openai.com/v1

ollama:
  baseUrl: http://localhost:11434
```

Ensure environment variable is set: `export OPENAI_API_KEY=sk-...`

### Agent Not Loading

**Problem**: Agent exists but not found
**Solution**: 
- Check file name: `AGENT.md` (case-sensitive, all uppercase)
- Verify directory structure: `hubbers-repo/src/main/resources/repo/agents/<name>/AGENT.md`
- Run `hubbers list agents` to see discovered agents

## Build and Deployment

### Maven Profiles

Default profile builds standard JAR:
```bash
mvn clean package
```

Native profile requires GraalVM:
```bash
mvn clean package -Pnative
```

### Native Image Configuration

Located in `hubbers-framework/src/main/resources/META-INF/native-image/`:
- `reflect-config.json` - Reflection classes
- `resource-config.json` - Embedded resources
- `jni-config.json` - JNI interfaces

### Installation Scripts

**Linux/macOS** (`install.sh`):
- Copies binary to `/usr/local/bin/hubbers`
- Dev mode: creates symlink instead

**Windows** (`install.bat`):
- Copies to `%ProgramFiles%\hubbers`
- Adds to system PATH

### Environment Variables

- `OPENAI_API_KEY` - OpenAI API key (required for OpenAI models)
- `HUBBERS_REPO` - Repository root path (default: `hubbers-repo/src/main/resources/repo` or external path via --repo flag)
- `FIRECRAWL_API_KEY` - For Firecrawl web scraping (optional)

## CLI Commands Reference

```bash
# List artifacts
hubbers list agents
hubbers list tools
hubbers list pipelines
hubbers list skills

# Execute artifacts
hubbers agent run <name> --input <json>
hubbers tool run <name> --input <json>
hubbers pipeline run <name> --input <json>
hubbers skill run <name> --input <json>

# Validate
hubbers skill validate <name>

# Web UI
hubbers web --port 7070

# Natural language tasks
hubbers task run --input '{"request":"Your natural language request"}'
```

## Contributing Checklist

Before committing changes:
1. ✅ Run `mvn test` - all tests pass
2. ✅ Run `mvn package` - build succeeds
3. ✅ Test artifact manually: `hubbers <type> run <name>`
4. ✅ Validate schemas in YAML/MD files
5. ✅ Update documentation if adding new features
6. ✅ Follow code style conventions
7. ✅ Add JavaDoc for public methods
8. ✅ Check for compilation errors with GraalVM (if modifying core)
