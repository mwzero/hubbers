---
description: "Testing standards and best practices for Hubbers framework. Use when: writing unit tests, creating test classes, testing executors, testing artifact loading, debugging test failures."
applyTo: "**/src/test/**/*.java"
---

# Testing Standards

## Test Framework Stack

- **JUnit 5 (Jupiter)** - Test framework
- **AssertJ** - Fluent assertions (if available)
- **Manual Mocking** - No Mockito (keep lightweight)

## Test Class Structure

```java
package org.hubbers.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentExecutor Tests")
class AgentExecutorTest {
    
    private AgentExecutor executor;
    private TestLLMClient mockClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // Initialize test fixtures
        objectMapper = JacksonFactory.createObjectMapper();
        mockClient = new TestLLMClient();
        executor = new AgentExecutor(mockClient, objectMapper);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up resources if needed
    }
    
    @Test
    @DisplayName("Should execute agent successfully with valid input")
    void testExecuteAgent_WithValidInput_ReturnsSuccess() {
        // Test implementation
    }
}
```

## Test Method Naming

### Pattern: `test<Method>_<Scenario>_<ExpectedBehavior>`

```java
// ✅ Descriptive test names
@Test
void testExecute_WithValidInput_ReturnsSuccessResult() {}

@Test
void testExecute_WithMissingRequiredField_ThrowsValidationException() {}

@Test
void testExecute_WithInvalidAgentName_ThrowsManifestNotFoundException() {}

@Test
void testLoadManifest_WithExistingAgent_ReturnsPopulatedManifest() {}

@Test
void testLoadManifest_WithNonexistentAgent_ReturnsEmptyOptional() {}

// ❌ Vague test names
@Test
void testExecute() {}

@Test
void test1() {}

@Test
void testSuccess() {}
```

## Test Structure: Given-When-Then

```java
@Test
void testExecuteAgent_WithValidInput_ReturnsSuccessResult() {
    // Given - Set up test data and preconditions
    AgentManifest manifest = AgentManifest.builder()
        .name("test.agent")
        .version("1.0.0")
        .build();
    JsonNode input = objectMapper.readTree("{\"query\": \"test\"}");
    
    // When - Execute the method under test
    RunResult result = executor.execute("test.agent", input);
    
    // Then - Verify the outcome
    assertTrue(result.isSuccess());
    assertNotNull(result.getOutput());
    assertEquals("test", result.getOutput().get("query").asText());
}
```

## Assertions

### JUnit 5 Assertions

```java
// ✅ Use descriptive assertion messages
assertEquals(expected, actual, "Agent name should match");
assertTrue(result.isSuccess(), "Execution should succeed");
assertFalse(list.isEmpty(), "List should contain results");
assertNotNull(output, "Output should not be null");

// ✅ Use assertThrows for exceptions
ValidationException exception = assertThrows(
    ValidationException.class,
    () -> executor.execute("test.agent", invalidInput),
    "Should throw ValidationException for invalid input"
);
assertEquals("Missing required field: query", exception.getMessage());

// ✅ Use assertAll for multiple related assertions
assertAll("Agent manifest validation",
    () -> assertEquals("test.agent", manifest.getName()),
    () -> assertEquals("1.0.0", manifest.getVersion()),
    () -> assertNotNull(manifest.getModel())
);
```

### AssertJ (if available)

```java
// ✅ Use fluent assertions
assertThat(result.isSuccess()).isTrue();
assertThat(result.getOutput()).isNotNull();
assertThat(agents).hasSize(3).contains("demo.search");

assertThat(manifest)
    .extracting(AgentManifest::getName, AgentManifest::getVersion)
    .containsExactly("test.agent", "1.0.0");
```

## Test Data Creation

### Use Builder Pattern

```java
// ✅ Create reusable test builders
private AgentManifest createTestManifest() {
    return AgentManifest.builder()
        .name("test.agent")
        .version("1.0.0")
        .description("Test agent")
        .model(createTestModelConfig())
        .build();
}

private ModelConfig createTestModelConfig() {
    return ModelConfig.builder()
        .provider("openai")
        .name("gpt-4")
        .temperature(0.7)
        .build();
}

// ✅ Use test fixtures in @BeforeEach
@BeforeEach
void setUp() {
    testManifest = createTestManifest();
    testInput = createValidInput();
}
```

### Use Test Resources

```java
// ✅ Load test data from resources
@Test
void testLoadAgent_FromFile_ParsesCorrectly() throws Exception {
    Path testFile = Paths.get(getClass()
        .getResource("/test-agents/demo.search/AGENT.md")
        .toURI());
    
    AgentManifest manifest = loader.load(testFile);
    
    assertEquals("demo.search", manifest.getName());
}
```

## Manual Mocking (No Mockito)

### Create Test Doubles

```java
// ✅ Create simple mock implementations
class TestLLMClient implements LLMClient {
    private String responseToReturn;
    private List<String> receivedPrompts = new ArrayList<>();
    
    public void setResponse(String response) {
        this.responseToReturn = response;
    }
    
    @Override
    public String complete(String prompt) {
        receivedPrompts.add(prompt);
        return responseToReturn;
    }
    
    public List<String> getReceivedPrompts() {
        return receivedPrompts;
    }
}

// Usage in tests
@BeforeEach
void setUp() {
    mockClient = new TestLLMClient();
    mockClient.setResponse("{\"result\": \"success\"}");
    executor = new AgentExecutor(mockClient);
}

@Test
void testExecute_CallsLLMWithCorrectPrompt() {
    executor.execute("test.agent", input);
    
    List<String> prompts = mockClient.getReceivedPrompts();
    assertEquals(1, prompts.size());
    assertTrue(prompts.get(0).contains("test query"));
}
```

### Use Test Stubs

```java
// ✅ Create stub implementations for interfaces
class StubArtifactRepository implements ArtifactRepository {
    private Map<String, AgentManifest> agents = new HashMap<>();
    
    public void addAgent(AgentManifest manifest) {
        agents.put(manifest.getName(), manifest);
    }
    
    @Override
    public Optional<AgentManifest> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }
}
```

## Testing Executors

### AgentExecutor Tests

```java
@Test
void testExecuteAgent_WithValidInput_CallsLLMAndReturnsResult() {
    // Given
    AgentManifest manifest = createTestManifest();
    JsonNode input = objectMapper.readTree("{\"query\": \"test\"}");
    mockLLM.setResponse("{\"result\": \"success\"}");
    
    // When
    RunResult result = executor.execute("test.agent", input);
    
    // Then
    assertTrue(result.isSuccess());
    JsonNode output = result.getOutput();
    assertEquals("success", output.get("result").asText());
}

@Test
void testExecuteAgent_WithInvalidInput_ThrowsValidationException() {
    // Given
    JsonNode invalidInput = objectMapper.readTree("{}"); // Missing required field
    
    // When/Then
    assertThrows(ValidationException.class, () -> {
        executor.execute("test.agent", invalidInput);
    });
}
```

### ToolExecutor Tests

```java
@Test
void testExecuteTool_WithRegisteredDriver_CallsDriverExecute() {
    // Given
    TestToolDriver driver = new TestToolDriver();
    executor.registerDriver("test.tool", driver);
    JsonNode input = objectMapper.readTree("{\"param\": \"value\"}");
    
    // When
    RunResult result = executor.execute("test.tool", input);
    
    // Then
    assertTrue(driver.wasExecuted());
    assertEquals(input, driver.getReceivedInput());
}
```

### PipelineExecutor Tests

```java
@Test
void testExecutePipeline_WithMultipleSteps_ExecutesInOrder() {
    // Given
    PipelineManifest pipeline = createTestPipeline();
    JsonNode input = objectMapper.readTree("{\"url\": \"https://example.com\"}");
    
    // When
    RunResult result = pipelineExecutor.execute("test.pipeline", input);
    
    // Then
    assertTrue(result.isSuccess());
    // Verify steps executed in order
    verify(stepExecution).executedSteps(List.of("fetch", "process", "analyze"));
}
```

## Testing Artifact Loading

### Manifest Loader Tests

```java
@Test
void testLoadAgent_WithValidManifest_ParsesAllFields() throws Exception {
    // Given
    Path manifestPath = createTestManifestFile();
    
    // When
    AgentManifest manifest = loader.load(manifestPath);
    
    // Then
    assertAll("Agent manifest fields",
        () -> assertEquals("test.agent", manifest.getName()),
        () -> assertEquals("1.0.0", manifest.getVersion()),
        () -> assertNotNull(manifest.getModel()),
        () -> assertNotNull(manifest.getInput()),
        () -> assertNotNull(manifest.getOutput())
    );
}

@Test
void testLoadAgent_WithMalformedYAML_ThrowsManifestLoadException() {
    // Given
    Path invalidManifest = Paths.get("src/test/resources/invalid-agent.md");
    
    // When/Then
    assertThrows(ManifestLoadException.class, () -> {
        loader.load(invalidManifest);
    });
}
```

## Testing JSON Schema Validation

```java
@Test
void testValidateInput_WithValidData_ReturnsTrue() {
    // Given
    JsonNode schema = loadSchema("agent-input-schema.json");
    JsonNode validInput = objectMapper.readTree("{\"query\": \"test\", \"maxResults\": 10}");
    
    // When
    boolean isValid = validator.validate(schema, validInput);
    
    // Then
    assertTrue(isValid);
}

@Test
void testValidateInput_WithMissingRequiredField_ReturnsFalse() {
    // Given
    JsonNode schema = loadSchema("agent-input-schema.json");
    JsonNode invalidInput = objectMapper.readTree("{\"maxResults\": 10}"); // Missing 'query'
    
    // When
    boolean isValid = validator.validate(schema, invalidInput);
    
    // Then
    assertFalse(isValid);
}
```

## Parameterized Tests

```java
// ✅ Test multiple scenarios with @ParameterizedTest
@ParameterizedTest
@ValueSource(strings = {"openai", "ollama", "anthropic"})
void testModelProvider_WithValidProvider_CreatesClient(String provider) {
    ModelConfig config = ModelConfig.builder()
        .provider(provider)
        .name("test-model")
        .build();
    
    LLMClient client = clientFactory.create(config);
    
    assertNotNull(client);
}

@ParameterizedTest
@CsvSource({
    "demo.search, 1.0.0, true",
    "research.assistant, 1.0.0, true",
    "nonexistent.agent, 1.0.0, false"
})
void testAgentExists_WithVariousNames_ReturnsExpectedResult(
        String name, String version, boolean shouldExist) {
    boolean exists = repository.agentExists(name);
    assertEquals(shouldExist, exists);
}
```

## Integration Tests

### Naming Convention

```java
// ✅ Use IT suffix for integration tests
class AgentExecutorIT {
    // Integration tests that hit real LLM APIs or file system
}

class ArtifactRepositoryIT {
    // Integration tests that load real manifests from file system
}
```

### Setup and Teardown

```java
@BeforeEach
void setUp() {
    // Create temp directory for test artifacts
    testRepoPath = Files.createTempDirectory("hubbers-test");
    repository = new ArtifactRepository(testRepoPath);
}

@AfterEach
void tearDown() throws IOException {
    // Clean up test directory
    deleteRecursively(testRepoPath);
}
```

## Test Coverage Guidelines

### Aim for High Coverage on:
- ✅ Core execution paths (executors)
- ✅ Manifest loading and parsing
- ✅ Input/output validation
- ✅ Error handling paths
- ✅ Business logic

### Don't Over-Test:
- ❌ Simple getters/setters (Lombok-generated)
- ❌ Trivial constructors
- ❌ Pure data classes (records)

## Testing Best Practices

### DO:
- ✅ Test one thing per test method
- ✅ Make tests independent (no shared mutable state)
- ✅ Use descriptive test names
- ✅ Follow Given-When-Then structure
- ✅ Test both success and failure paths
- ✅ Test edge cases and boundary conditions
- ✅ Keep tests fast (avoid sleep, network calls)
- ✅ Use test fixtures and builders
- ✅ Clean up resources in @AfterEach

### DON'T:
- ❌ Test implementation details
- ❌ Make tests depend on each other
- ❌ Use random data without seed
- ❌ Suppress exceptions in tests
- ❌ Use Thread.sleep() (use deterministic waits)
- ❌ Test multiple scenarios in one test
- ❌ Leave commented-out test code
- ❌ Skip writing tests for "simple" code

## Common Test Patterns

### Testing Optional Return Values

```java
@Test
void testFindAgent_WhenExists_ReturnsOptionalWithValue() {
    Optional<AgentManifest> result = repository.findAgent("demo.search");
    
    assertTrue(result.isPresent());
    assertEquals("demo.search", result.get().getName());
}

@Test
void testFindAgent_WhenNotExists_ReturnsEmptyOptional() {
    Optional<AgentManifest> result = repository.findAgent("nonexistent");
    
    assertTrue(result.isEmpty());
}
```

### Testing Exception Messages

```java
@Test
void testValidate_WithInvalidInput_ThrowsExceptionWithDetailedMessage() {
    Exception exception = assertThrows(ValidationException.class, () -> {
        validator.validate(schema, invalidInput);
    });
    
    assertTrue(exception.getMessage().contains("Missing required field: query"));
}
```

### Testing Collections

```java
@Test
void testListAgents_ReturnsAllAvailableAgents() {
    List<String> agents = repository.listAgents();
    
    assertNotNull(agents);
    assertTrue(agents.size() >= 3);
    assertTrue(agents.contains("demo.search"));
    assertTrue(agents.contains("research.assistant"));
}
```

## Debugging Failed Tests

### Add Diagnostic Output

```java
@Test
void testComplexScenario() {
    RunResult result = executor.execute("test.agent", input);
    
    // Add diagnostic output for debugging
    System.out.println("Execution result: " + result);
    System.out.println("Output: " + result.getOutput());
    
    assertTrue(result.isSuccess(), 
        "Expected success but got: " + result.getError());
}
```

### Use @DisplayName for Context

```java
@Test
@DisplayName("Execute agent with missing API key should throw ConfigurationException")
void testMissingApiKey() {
    // Test implementation
}
```

## Test File Organization

```
src/test/java/org/hubbers/
├── agent/
│   ├── AgentExecutorTest.java
│   ├── AgentManifestLoaderTest.java
│   └── AgenticExecutorTest.java
├── tool/
│   ├── ToolExecutorTest.java
│   └── drivers/
│       ├── FirecrawlDriverTest.java
│       └── RssReaderDriverTest.java
├── pipeline/
│   └── PipelineExecutorTest.java
├── skill/
│   └── SkillExecutorTest.java
└── TestFixtures.java  # Shared test utilities

src/test/resources/
├── test-agents/
│   └── demo.search/
│       └── AGENT.md
├── test-tools/
│   └── test.tool/
│       └── tool.yaml
└── test-data/
    └── sample-inputs.json
```

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AgentExecutorTest

# Run specific test method
mvn test -Dtest=AgentExecutorTest#testExecute_WithValidInput_ReturnsSuccess

# Run tests with pattern
mvn test -Dtest=*Agent*

# Run integration tests only
mvn test -Dtest=*IT

# Skip tests during build
mvn package -DskipTests

# Show test output
mvn test -X
```

## Test Checklist

Before committing:
- [ ] All tests pass locally (`mvn test`)
- [ ] New code has corresponding tests
- [ ] Tests follow naming conventions
- [ ] Tests use Given-When-Then structure
- [ ] Both success and failure paths tested
- [ ] No commented-out test code
- [ ] No @Disabled tests without explanation
- [ ] Tests are independent and repeatable
- [ ] Test resources cleaned up properly
- [ ] Descriptive assertion messages included
