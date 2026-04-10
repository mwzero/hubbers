# Tool Development Guide

Documentation for creating and modifying tools in the Hubbers framework.

## What Are Tools?

Tools are **reusable functions** that agents and pipelines can invoke to perform specific actions:
- File operations (read, write, list)
- Web scraping (RSS feeds, browser automation)
- Data processing (CSV, PDF, vector search)
- External services (weather API, process management)

Tools are **synchronous** and **deterministic** - they take input and return output without internal state.

## Tool Format

Tools use YAML format (`manifest.yaml`):

```yaml
tool:
  name: my-tool
  version: 1.0.0
  description: What this tool does

input:
  schema:
    type: object
    properties:
      param1:
        type: string
        required: true

output:
  schema:
    type: object
    properties:
      result:
        type: string

driver:
  class: org.hubbers.tools.MyTool
```

## Field Reference

### Required Fields

#### `tool`
Metadata:
- `name` (string): Unique identifier, kebab-case (e.g., "file-ops")
- `version` (string): Semantic version (e.g., "1.0.0")
- `description` (string): Human-readable description

#### `driver`
Java implementation:
- `class` (string): Fully qualified class name implementing `ToolDriver`

#### `input` / `output`
JSON Schema definitions (see Agent guide for schema syntax).

### Optional Fields

#### `config`
Tool-specific configuration:
```yaml
config:
  max_size_bytes: 10485760
  allowed_extensions: [".txt", ".md"]
```

## Tool Implementation

### 1. Create Manifest

Create `repo/tools/my-tool/manifest.yaml`:

```yaml
tool:
  name: my-tool
  version: 1.0.0
  description: Performs a specific action

input:
  schema:
    type: object
    properties:
      param1: {type: string, required: true}

output:
  schema:
    type: object
    properties:
      result: {type: string}

driver:
  class: org.hubbers.tools.MyToolDriver
```

### 2. Implement ToolDriver

Create `src/main/java/org/hubbers/tools/MyToolDriver.java`:

```java
package org.hubbers.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.executor.artifact.ToolDriver;
import org.hubbers.executor.context.ExecutionContext;
import org.hubbers.model.RunResult;

@Slf4j
public class MyToolDriver implements ToolDriver {
    
    @Override
    public RunResult execute(JsonNode input, ExecutionContext context) {
        try {
            // 1. Extract parameters
            String param1 = input.get("param1").asText();
            
            // 2. Validate input (optional - schemas handle this)
            if (param1 == null || param1.isEmpty()) {
                return RunResult.failed("param1 is required");
            }
            
            // 3. Perform the action
            String result = performAction(param1);
            
            // 4. Return success
            return RunResult.success(
                JacksonFactory.createObjectNode()
                    .put("result", result)
            );
            
        } catch (Exception e) {
            log.error("Tool execution failed", e);
            return RunResult.failed("Error: " + e.getMessage());
        }
    }
    
    private String performAction(String param1) {
        // Your implementation here
        return "Processed: " + param1;
    }
}
```

### 3. Register Tool (GraalVM Native)

Add reflection config in `src/main/resources/META-INF/native-image/.../reflect-config.json`:

```json
{
  "name": "org.hubbers.tools.MyToolDriver",
  "allDeclaredConstructors": true,
  "allPublicMethods": true
}
```

### 4. Test Tool

```bash
# Test with CLI
hubbers tool run my-tool --input '{"param1":"value"}'

# Or use in agent
# Add "my-tool" to agent's tools list
```

## Tool Patterns

### Pattern 1: Simple I/O Tool

**Use case**: Stateless transformation (file read, format conversion)
**Examples**: file.ops, csv.read, pdf.extract

```java
@Slf4j
public class SimpleToolDriver implements ToolDriver {
    @Override
    public RunResult execute(JsonNode input, ExecutionContext context) {
        try {
            // Extract
            String inputData = input.get("data").asText();
            
            // Process
            String outputData = process(inputData);
            
            // Return
            return RunResult.success(
                JacksonFactory.createObjectNode()
                    .put("result", outputData)
            );
        } catch (Exception e) {
            return RunResult.failed(e.getMessage());
        }
    }
}
```

### Pattern 2: External API Tool

**Use case**: Call external service (weather, geocoding, web scraping)
**Examples**: weather.lookup, rss.fetch

```java
@Slf4j
public class ApiToolDriver implements ToolDriver {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    @Override
    public RunResult execute(JsonNode input, ExecutionContext context) {
        try {
            String apiUrl = buildApiUrl(input);
            
            // HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                return RunResult.failed("API error: " + response.statusCode());
            }
            
            // Parse response
            JsonNode data = JacksonFactory.jsonMapper().readTree(response.body());
            
            return RunResult.success(data);
            
        } catch (Exception e) {
            return RunResult.failed(e.getMessage());
        }
    }
}
```

### Pattern 3: File System Tool

**Use case**: Read/write files (backup, CSV processing, PDF extraction)
**Examples**: file.ops, csv.write

```java
@Slf4j
public class FileToolDriver implements ToolDriver {
    @Override
    public RunResult execute(JsonNode input, ExecutionContext context) {
        try {
            String filePath = input.get("path").asText();
            Path path = context.getDataPath().resolve(filePath);
            
            // Security check
            if (!path.normalize().startsWith(context.getDataPath())) {
                return RunResult.failed("Path traversal not allowed");
            }
            
            // File operation
            String content = Files.readString(path);
            
            return RunResult.success(
                JacksonFactory.createObjectNode()
                    .put("content", content)
            );
            
        } catch (IOException e) {
            return RunResult.failed("File error: " + e.getMessage());
        }
    }
}
```

### Pattern 4: Stateful Tool (Context-Aware)

**Use case**: Tools needing execution state (vector DB, process tracking)
**Examples**: lucene.kv, process.manage

```java
@Slf4j
public class StatefulToolDriver implements ToolDriver {
    @Override
    public RunResult execute(JsonNode input, ExecutionContext context) {
        try {
            String operation = input.get("operation").asText();
            
            // Access context state
            Path dataDir = context.getDataPath();
            String executionId = context.getExecutionId();
            
            // Persistent state (e.g., Lucene index)
            Path indexPath = dataDir.resolve("lucene-" + executionId);
            
            switch (operation) {
                case "write":
                    return writeOperation(input, indexPath);
                case "read":
                    return readOperation(input, indexPath);
                default:
                    return RunResult.failed("Unknown operation: " + operation);
            }
            
        } catch (Exception e) {
            return RunResult.failed(e.getMessage());
        }
    }
}
```

## Best Practices

### Error Handling

1. **Catch specific exceptions**: Don't use bare `catch (Exception e)`
2. **Return descriptive errors**: `RunResult.failed("Clear error message")`
3. **Log with context**: `log.error("Tool failed for input: {}", input, e)`
4. **Validate early**: Check inputs before expensive operations

### Security

1. **Path traversal**: Validate file paths against `context.getDataPath()`
2. **Command injection**: Sanitize inputs before shell execution
3. **Resource limits**: Set max file size, request timeout, iteration limits
4. **Credentials**: Use environment variables, never hardcode

### Performance

1. **Reuse connections**: Create HTTP clients once, not per request
2. **Stream large files**: Use `Files.lines()` instead of `Files.readString()`
3. **Batch operations**: Process multiple items in one call
4. **Timeout**: Set reasonable timeout for external APIs

### Testing

```java
@Test
void testMyTool() {
    // Setup
    MyToolDriver driver = new MyToolDriver();
    ExecutionContext context = ExecutionContext.builder()
        .dataPath(tempDir)
        .executionId("test-123")
        .build();
    
    JsonNode input = JacksonFactory.createObjectNode()
        .put("param1", "value");
    
    // Execute
    RunResult result = driver.execute(input, context);
    
    // Assert
    assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
    assertNotNull(result.getOutput().get("result"));
}
```

## Tool Examples

### file.ops

**Purpose**: Read, write, list files

```yaml
tool:
  name: file.ops
  version: 1.0.0
  description: File system operations

input:
  schema:
    type: object
    properties:
      operation: {type: string, enum: [read, write, list]}
      path: {type: string}
      content: {type: string}  # For write

output:
  schema:
    type: object
    properties:
      result: {type: string}
      files: {type: array}     # For list

driver:
  class: org.hubbers.tools.FileOpsDriver
```

### csv.read

**Purpose**: Parse CSV files

```yaml
tool:
  name: csv.read
  version: 1.0.0
  description: Read CSV files

input:
  schema:
    type: object
    properties:
      file_path: {type: string, required: true}
      delimiter: {type: string, default: ","}

output:
  schema:
    type: object
    properties:
      headers: {type: array}
      rows: {type: array}

driver:
  class: org.hubbers.tools.CsvReadDriver
```

### rss.fetch

**Purpose**: Fetch RSS/Atom feeds

```yaml
tool:
  name: rss.fetch
  version: 1.0.0
  description: Fetch RSS/Atom feeds

input:
  schema:
    type: object
    properties:
      url: {type: string, required: true}
      max_items: {type: number, default: 10}

output:
  schema:
    type: object
    properties:
      items:
        type: array
        items:
          type: object
          properties:
            title: {type: string}
            link: {type: string}
            description: {type: string}
            published_date: {type: string}

driver:
  class: org.hubbers.tools.RssFetchDriver
```

## Troubleshooting

### Tool Not Found

- Check manifest location: `repo/tools/<name>/manifest.yaml`
- Run `hubbers list tools` to verify discovery
- Check tool name matches directory name

### ClassNotFoundException

- Verify `driver.class` matches Java package
- For native build: Add class to `reflect-config.json`
- Rebuild: `mvn clean package` or `build-native.sh`

### Schema Validation Errors

- Test input schema: https://www.jsonschemavalidator.net/
- Check required vs optional fields
- Verify default values are appropriate

### Native Build Issues

For GraalVM native image:
1. Add reflection config for driver class
2. Add resource config for external files
3. Use `--initialize-at-build-time` for static data
4. Test with JVM first (`mvn package && java -jar target/*.jar`)

## Related Documentation

- [Agent Development Guide](../agents/AGENTS.md) - How agents use tools
- [Pipeline Development Guide](../pipelines/AGENTS.md) - Orchestrating multiple tools
- Root AGENTS.md - Project setup and architecture
