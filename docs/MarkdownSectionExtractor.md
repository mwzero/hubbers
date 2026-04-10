# MarkdownSectionExtractor Utility

Reusable utility for extracting Markdown sections and parsing JSON code blocks.

## Purpose

Parse pure Markdown files with structured sections containing JSON configuration:
- Extract sections by `## Heading` patterns
- Parse ```json code blocks within sections
- Return JsonNode objects for structured data
- Handle optional sections gracefully

## Usage

```java
MarkdownSectionExtractor extractor = new MarkdownSectionExtractor();

// Extract section content
String configSection = extractor.extractSection(content, "Configuration");

// Parse JSON from code block within section
JsonNode configJson = extractor.extractJsonBlock(configSection);

// Or combined
JsonNode modelJson = extractor.extractSectionAsJson(content, "Model");
```

## Implementation

Located at: `src/main/java/org/hubbers/util/MarkdownSectionExtractor.java`

### Methods

1. **`extractSection(String content, String heading)`** → String
   - Extracts text between `## Heading` and next `##` or EOF
   - Returns null if section not found

2. **`extractJsonBlock(String sectionContent)`** → JsonNode
   - Finds first ```json code block in section
   - Parses with Jackson ObjectMapper
   - Returns null if no code block found

3. **`extractSectionAsJson(String content, String heading)`** → JsonNode
   - Convenience method combining extractSection + extractJsonBlock

4. **`extractSectionText(String content, String heading)`** → String
   - Extracts plain text (non-code-block content) from section
   - Useful for ## Instructions section

### Error Handling

- Throws `IOException` for malformed JSON
- Returns `null` for missing sections (allows optional sections)
- Logs warnings for parsing issues

## Example: Agent Configuration

**Markdown Input:**
```markdown
## Configuration

```json
{
  "name": "universal.task",
  "version": "1.0.0",
  "description": "Universal task agent",
  "tools": [],
  "maxIterations": 15
}
```

## Model

```json
{
  "provider": "ollama",
  "name": "qwen2.5-coder:7b",
  "temperature": 0.1
}
```
```

**Java Usage:**
```java
ObjectMapper mapper = JacksonFactory.jsonMapper();
MarkdownSectionExtractor extractor = new MarkdownSectionExtractor(mapper);

JsonNode config = extractor.extractSectionAsJson(content, "Configuration");
String name = config.get("name").asText(); // "universal.task"

JsonNode model = extractor.extractSectionAsJson(content, "Model");
String provider = model.get("provider").asText(); // "ollama"
```
