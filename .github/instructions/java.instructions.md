---
description: "Java 21 coding standards for Hubbers framework. Use when: writing or modifying Java source files, creating new classes, refactoring code, implementing executors or drivers."
applyTo: "**/*.java"
---

# Java Coding Standards

## Java 21 Features - Use These!

### Records for Data Transfer Objects

```java
// ✅ Use records for immutable DTOs
public record AgentConfig(String name, String version, ModelConfig model) {}

// ❌ Don't create traditional POJOs with getters/setters for simple data carriers
public class AgentConfig {
    private String name;
    private String version;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

### Pattern Matching and Switch Expressions

```java
// ✅ Use pattern matching with switch expressions
public String describeArtifact(Artifact artifact) {
    return switch (artifact) {
        case AgentManifest a -> "Agent: " + a.getName();
        case ToolManifest t -> "Tool: " + t.getName();
        case PipelineManifest p -> "Pipeline: " + p.getName();
        case SkillManifest s -> "Skill: " + s.getName();
        case null -> "Unknown";
    };
}

// ❌ Don't use old-style instanceof chains
public String describeArtifact(Artifact artifact) {
    if (artifact instanceof AgentManifest) {
        AgentManifest a = (AgentManifest) artifact;
        return "Agent: " + a.getName();
    } else if (artifact instanceof ToolManifest) {
        ToolManifest t = (ToolManifest) artifact;
        return "Tool: " + t.getName();
    }
    // ...
}
```

### Text Blocks for Multi-line Strings

```java
// ✅ Use text blocks for JSON, YAML, SQL, etc.
String jsonSchema = """
    {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "version": {"type": "string"}
        }
    }
    """;

// ❌ Don't concatenate strings with \n
String jsonSchema = "{\n" +
    "  \"type\": \"object\",\n" +
    "  \"properties\": {\n" +
    // ...
```

### Sealed Classes for Controlled Hierarchies

```java
// ✅ Use sealed classes when you control all subtypes
public sealed interface RunResult permits Success, Error, Pending {
    record Success(JsonNode output) implements RunResult {}
    record Error(String message, Throwable cause) implements RunResult {}
    record Pending(String taskId) implements RunResult {}
}
```

## Lombok Usage

### Common Annotations

```java
// ✅ Use @Data for mutable beans
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelConfig {
    private String provider;
    private String name;
    private double temperature;
}

// ✅ Use @Builder for complex object creation
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionContext {
    private String userId;
    private Map<String, Object> variables;
    private List<String> tools;
    private Duration timeout;
}

// ✅ Use @Slf4j for logging
@Slf4j
public class AgentExecutor {
    public void execute() {
        log.info("Executing agent");
    }
}
```

## Functional Programming Patterns

### Use Streams Over Loops

```java
// ✅ Use streams for collection processing
List<String> activeAgents = agents.stream()
    .filter(Agent::isActive)
    .map(Agent::getName)
    .sorted()
    .collect(Collectors.toList());

// ❌ Avoid traditional loops for simple transformations
List<String> activeAgents = new ArrayList<>();
for (Agent agent : agents) {
    if (agent.isActive()) {
        activeAgents.add(agent.getName());
    }
}
Collections.sort(activeAgents);
```

### Use Optional for Null Safety

```java
// ✅ Return Optional for methods that might not find a value
public Optional<AgentManifest> findAgent(String name) {
    Path path = agentsPath.resolve(name).resolve("AGENT.md");
    if (!Files.exists(path)) {
        return Optional.empty();
    }
    return Optional.of(loadManifest(path));
}

// ✅ Use Optional methods for safe chaining
String agentVersion = findAgent("demo.search")
    .map(AgentManifest::getVersion)
    .orElse("unknown");

// ❌ Don't return null
public AgentManifest findAgent(String name) {
    // ...
    return null; // BAD!
}
```

### Use Method References

```java
// ✅ Use method references when possible
agents.stream()
    .map(Agent::getName)
    .forEach(log::info);

// ❌ Don't use lambda when method reference works
agents.stream()
    .map(agent -> agent.getName())
    .forEach(name -> log.info(name));
```

## Exception Handling

### Be Specific with Exceptions

```java
// ✅ Catch specific exceptions
public RunResult execute(String name, JsonNode input) {
    try {
        return doExecute(name, input);
    } catch (IOException e) {
        log.error("Failed to read manifest: {}", name, e);
        return RunResult.error("IO error: " + e.getMessage());
    } catch (JsonProcessingException e) {
        log.error("Invalid JSON input", e);
        return RunResult.error("Invalid JSON: " + e.getMessage());
    }
}

// ❌ Don't catch generic Exception unless truly necessary
try {
    return doExecute(name, input);
} catch (Exception e) {  // Too broad!
    return RunResult.error(e.getMessage());
}
```

### Never Swallow Exceptions

```java
// ✅ Always log exceptions
try {
    riskyOperation();
} catch (IOException e) {
    log.error("Operation failed", e);
    throw new RuntimeException("Failed to execute", e);
}

// ❌ Never do this!
try {
    riskyOperation();
} catch (IOException e) {
    // Silent failure - very bad!
}
```

## Class Design

### Single Responsibility Principle

```java
// ✅ Each class has one clear purpose
@Slf4j
@AllArgsConstructor
public class AgentManifestLoader {
    private final ObjectMapper objectMapper;
    
    public AgentManifest load(Path path) throws IOException {
        // Only responsible for loading manifests
    }
}

@Slf4j
@AllArgsConstructor
public class AgentExecutor {
    private final LLMClient llmClient;
    private final AgentManifestLoader loader;
    
    public RunResult execute(String name, JsonNode input) {
        // Only responsible for executing agents
    }
}
```

### Keep Methods Short and Focused

```java
// ✅ Break complex methods into smaller ones
public RunResult execute(String name, JsonNode input) {
    AgentManifest manifest = loadManifest(name);
    validateInput(manifest, input);
    String prompt = buildPrompt(manifest, input);
    String response = callLLM(prompt);
    return parseResponse(response);
}

// ❌ Don't create huge methods that do everything
public RunResult execute(String name, JsonNode input) {
    // 200 lines of code doing everything...
}
```

## JSON Processing

### Use Jackson with Type References

```java
// ✅ Use TypeReference for complex types
Map<String, Object> config = objectMapper.readValue(
    json,
    new TypeReference<Map<String, Object>>() {}
);

// ✅ Use JsonNode for flexible parsing
JsonNode root = objectMapper.readTree(json);
String name = root.path("agent").path("name").asText();

// ✅ Deserialize to POJOs when structure is known
AgentManifest manifest = objectMapper.readValue(yaml, AgentManifest.class);
```

### Handle JSON Errors Gracefully

```java
// ✅ Catch JSON processing exceptions
try {
    return objectMapper.readValue(json, AgentManifest.class);
} catch (JsonProcessingException e) {
    log.error("Invalid JSON structure", e);
    throw new ManifestLoadException("Invalid manifest format", e);
}
```

## Concurrency Considerations

### Use Immutable Objects

```java
// ✅ Make objects immutable for thread safety
public record ExecutionResult(
    String agentName,
    Instant timestamp,
    JsonNode output
) {
    // Records are immutable by default
}

// ✅ Use final fields in classes
@AllArgsConstructor
public class AgentExecutor {
    private final LLMClient llmClient;  // Can't be changed after construction
    private final ObjectMapper objectMapper;
}
```

## Performance Best Practices

### Avoid Unnecessary Object Creation

```java
// ✅ Reuse objects when possible
private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---$", Pattern.MULTILINE);

// ❌ Don't compile patterns repeatedly
public void parse(String content) {
    Pattern pattern = Pattern.compile("^---$", Pattern.MULTILINE);  // BAD!
}
```

### Use Lazy Loading

```java
// ✅ Load data only when needed
public class ArtifactRepository {
    private Map<String, AgentMetadata> metadataCache = new HashMap<>();
    
    public AgentManifest getAgent(String name) {
        if (!metadataCache.containsKey(name)) {
            metadataCache.put(name, loadMetadata(name));
        }
        return loadFullManifest(name);  // Full manifest only when needed
    }
}
```

## Documentation Standards

### JavaDoc Requirements

```java
/**
 * Executes an agent with the given input.
 * 
 * @param name the name of the agent to execute (e.g., "demo.search")
 * @param input the input data as JSON, must match agent's input schema
 * @return the execution result containing output or error details
 * @throws ManifestNotFoundException if the agent doesn't exist
 * @throws ValidationException if input doesn't match schema
 */
public RunResult execute(String name, JsonNode input) 
        throws ManifestNotFoundException, ValidationException {
    // Implementation
}
```

### Inline Comments

```java
// ✅ Explain why, not what
// Use lazy evaluation to avoid loading all manifests at startup
private Map<String, AgentMetadata> metadataCache;

// ❌ Don't state the obvious
// Increment i
i++;
```

## Testing Patterns

### Test Method Naming

```java
// ✅ Use descriptive test names
@Test
void testExecuteAgent_WithValidInput_ReturnsSuccessResult() {
    // Given
    AgentManifest manifest = createTestManifest();
    JsonNode input = createValidInput();
    
    // When
    RunResult result = executor.execute("test.agent", input);
    
    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutput()).isNotNull();
}
```

### Use Test Fixtures

```java
// ✅ Create reusable test data
@BeforeEach
void setUp() {
    objectMapper = JacksonFactory.createObjectMapper();
    executor = new AgentExecutor(llmClient, manifestLoader);
    testInput = objectMapper.readTree("{\"query\": \"test\"}");
}
```

## Anti-Patterns to Avoid

❌ **Primitive Obsession** - Use domain objects instead of primitives
❌ **String Typing** - Use enums for fixed sets of values
❌ **Mutable Static State** - Avoid global mutable variables
❌ **Deep Nesting** - Keep indentation levels low (max 3-4)
❌ **Long Parameter Lists** - Use builder pattern or parameter objects
❌ **Magic Numbers** - Use named constants
❌ **God Classes** - Keep classes focused and small

## Code Review Checklist

Before submitting code:
- [ ] All public methods have JavaDoc
- [ ] No compiler warnings
- [ ] No magic numbers or strings
- [ ] Proper exception handling
- [ ] Tests pass (`mvn test`)
- [ ] Used Java 21 features where appropriate
- [ ] Followed naming conventions
- [ ] No `System.out.println()` (use logging)
- [ ] Null safety with Optional<T>
- [ ] Thread safety considered for shared state
