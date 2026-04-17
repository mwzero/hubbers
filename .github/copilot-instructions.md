# Hubbers Framework - Workspace Instructions

## Project Overview

Hubbers is a Git-native Java framework for executing AI agents, tools, pipelines, and skills defined as YAML/Markdown artifacts. This is a multi-module Maven project using Java 21, focusing on creating a lightweight runtime for agentic workflows without Spring framework dependencies.

## Core Principles

1. **Git-Native Architecture**: Repository as a source of truth for all artifacts (agents, tools, pipelines, skills)
2. **Lightweight Design**: Pure Java without Spring - use dependency injection patterns manually
3. **Functional Patterns**: Prefer streams over loops, immutability where possible, use `Optional` for null safety
4. **Modern Java**: Utilize Java 21 features (records, switch expressions, text blocks, pattern matching)
5. **Progressive Disclosure**: Lazy loading, metadata caching, on-demand manifest loading

## Technology Stack

- **Language**: Java 21 (target and source)
- **Build**: Maven multi-module project
- **JSON/YAML**: Jackson ObjectMapper via `JacksonFactory`
- **Logging**: SLF4J with Logback
- **Boilerplate Reduction**: Lombok (`@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder`)
- **Testing**: JUnit 5 (Jupiter)
- **Native Compilation**: GraalVM (optional, for native executables)

## Module Structure

- `hubbers-framework`: Core execution engine, artifact loading, executors
- `hubbers-repo`: Default artifact repository (agents, tools, pipelines, skills)
- `hubbers-distribution`: CLI and native image configuration
- `hubbers-ui`: React-based web interface (TypeScript/Vite)
- `sandbox`: Experimental features and prototypes

## Critical Files

- **Artifact Manifests**: Located in `hubbers-repo/src/main/resources/repo/{agents|tools|pipelines|skills}/`
  - Agent: `AGENT.md` (YAML frontmatter + Markdown instructions)
  - Tool: `tool.yaml`
  - Pipeline: `pipeline.yaml`
  - Skill: `SKILL.md` (follows agentskills.io spec)

- **Bootstrap**: `hubbers-framework/.../Bootstrap.java` - Dependency injection initialization
- **Runtime Facade**: `RuntimeFacade.java` - Main execution entry point
- **Executors**: `AgentExecutor`, `AgenticExecutor`, `ToolExecutor`, `PipelineExecutor`, `SkillExecutor`

## Code Quality Standards

### Always Follow

- Use descriptive variable names (avoid single letters except in lambda parameters)
- Add JavaDoc to all public methods and classes
- Include `@param`, `@return`, `@throws` in JavaDoc
- Keep methods focused (single responsibility)
- Prefer composition over inheritance
- Use immutable objects where possible (final fields, no setters)
- Handle exceptions appropriately (never swallow exceptions silently)

### Naming Conventions

- **Classes**: PascalCase (`AgentExecutor`, `RuntimeFacade`)
- **Methods**: camelCase (`execute()`, `loadManifest()`)
- **Variables**: camelCase (`artifactName`, `inputData`)
- **Constants**: UPPER_SNAKE_CASE (`DEFAULT_TIMEOUT`, `FRONTMATTER_DELIMITER`)
- **Packages**: lowercase (`org.hubbers.agent`, `org.hubbers.tool`)

### Logging Standards

```java
// Use SLF4J logger with Lombok
@Slf4j
public class MyClass {
    public void myMethod() {
        log.debug("Detailed debug info: {}", details);
        log.info("User-facing info: {}", info);
        log.error("Error occurred", exception);
    }
}
```

## Testing Requirements

- Write unit tests for all new functionality
- Follow JUnit 5 patterns (use `@Test`, `@BeforeEach`, `@AfterEach`)
- Use descriptive test method names: `testExecuteAgent_WithValidInput_ReturnsSuccess()`
- Mock dependencies manually (no Mockito - keep lightweight)
- Aim for high coverage on core execution paths

## Performance Considerations

- Lazy load artifacts (don't load all manifests at startup)
- Use caching where appropriate (metadata caching in skills)
- Stream processing for large datasets
- Avoid reflection where possible (GraalVM native image compatibility)

## Security Notes

- Never log sensitive data (API keys, credentials)
- Validate all input against JSON schemas
- Sanitize file paths (prevent directory traversal)
- Use secure defaults for API configurations

## Build Commands

```bash
# Install dependencies
mvn clean install

# Build JAR
mvn clean package

# Run tests
mvn test

# Build native image (requires GraalVM)
./build-native.sh  # Linux/macOS
build-native.bat   # Windows
```

## Documentation

- Keep AGENTS.md updated with new features
- Document architectural decisions in docs/SWA.md
- Update relevant docs in docs/ folder for major changes
- Include usage examples in README for new features

## Common Patterns

### Creating an Executor

```java
@Slf4j
@AllArgsConstructor
public class MyExecutor {
    private final ObjectMapper objectMapper;
    
    public RunResult execute(Manifest manifest, JsonNode input) {
        log.debug("Executing: {}", manifest.getName());
        // Implementation
        return RunResult.success(output);
    }
}
```

### Loading Artifacts

```java
public Optional<AgentManifest> loadAgent(String name) {
    Path path = repoPath.resolve("agents").resolve(name).resolve("AGENT.md");
    if (!Files.exists(path)) {
        return Optional.empty();
    }
    // Parse and return
    return Optional.of(manifest);
}
```

### Error Handling

```java
try {
    // Risky operation
} catch (IOException e) {
    log.error("Failed to load artifact: {}", name, e);
    return RunResult.error("Failed to load: " + e.getMessage());
}
```

## Anti-Patterns to Avoid

❌ Don't use null - use `Optional<T>` instead
❌ Don't use raw types - always parameterize generics
❌ Don't catch generic `Exception` - catch specific exceptions
❌ Don't use `System.out.println()` - use logging
❌ Don't hardcode paths - use configuration or constants
❌ Don't create God classes - follow single responsibility
❌ Don't ignore compiler warnings - fix them

## When Making Changes

1. Read relevant documentation in docs/ folder
2. Check existing patterns in similar classes
3. Write tests first (TDD approach encouraged)
4. Run `mvn test` before committing
5. Update documentation if adding features
6. Consider GraalVM compatibility (reflection configs)
