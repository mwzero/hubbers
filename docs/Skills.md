# Skills Development Guide

Documentation for creating and modifying skills in the Hubbers framework.

## What Are Skills?

Skills are **domain-specific instructions** that guide LLM behavior for specialized tasks:
- Provide step-by-step methodologies (e.g., how to summarize text)
- Define output formats and structures
- Embed best practices and domain knowledge
- Enable consistent, high-quality results
- Can be injected into agent prompts or invoked via skill executors

Skills use **Markdown format** (SKILL.md) with YAML frontmatter, following the [agentskills.io](https://agentskills.io) specification.

## Skills vs Agents

| Aspect | Skills | Agents |
|--------|--------|--------|
| **Purpose** | Instructions/knowledge | Executable workflows with ReAct/tools |
| **Format** | SKILL.md (documentation) | AGENT.md (config + instructions) |
| **Execution** | Via LLM prompt injection | Direct execution via AgentExecutor |
| **Invocation** | Injected into prompts | `hubbers agent run <name>` |
| **Use Case** | Reusable methodologies | Autonomous task execution |
| **Model Config** | No (methodology only) | Yes (provider, model, temperature) |
| **Tool Calling** | No (pure instructions) | Yes (can use ReAct loop) |

**Important**: Simple prompt-based tasks should be **skills**, not agents. Agents are for complex workflows that require:
- ReAct loop with autonomous decision-making
- Tool/function calling capabilities
- Form-based user interaction
- Orchestration of multiple steps

**Migration**: Former simple agents (rss.sentiment, rss.ner, rss.translate, text.summarizer) are now skills, as they're pure methodologies without tool calling.

## Skill Format

Skills use Markdown format (`SKILL.md`):

```markdown
---
name: skill-name
description: When to use this skill (shown to AI assistants)
metadata:
  execution_mode: llm-prompt|code-execution|hybrid
  author: team-name
  version: "1.0"
---

# Skill Name

## When to use this skill

Describe the scenarios where this skill should be invoked.

## How to perform the task

Step-by-step instructions for executing the skill:

1. **Step 1 name** - What to do first
2. **Step 2 name** - Next action
3. **Step 3 name** - Final steps

## Output format

Define the expected output structure:

```json
{
  "field": "value"
}
```

## Best practices

- Practice 1
- Practice 2

## Common pitfalls

- Pitfall 1: How to avoid
- Pitfall 2: How to avoid
```

## Field Reference

### YAML Frontmatter

#### Required Fields

- `name` (string): Unique identifier, kebab-case (e.g., "text-summarizer")
- `description` (string): When/why to use this skill (2-3 sentences)

#### Optional Fields

- `metadata.execution_mode` (string):
  - `llm-prompt`: Pure LLM instructions (default)
  - `code-execution`: Invoke code/tools during execution
  - `hybrid`: Combination of LLM + code
  
- `metadata.author` (string): Team or individual who created it
- `metadata.version` (string): Semantic version

### Markdown Body

#### Recommended Sections

1. **When to use this skill**: Triggering conditions
2. **How to perform the task**: Step-by-step methodology
3. **Output format**: Expected structure (with examples)
4. **Best practices**: Expert tips
5. **Common pitfalls**: What to avoid
6. **Examples**: Input/output samples (optional)

## Skill Patterns

### Pattern 1: Task Methodology Skill

**Use case**: Define how to perform a specific task
**Examples**: text-summarizer, data-analyzer

```markdown
---
name: task-methodology
description: Step-by-step approach for completing [task]
metadata:
  execution_mode: llm-prompt
  version: "1.0"
---

# Task Methodology

## When to use this skill

Use this skill when you need to:
- Scenario 1
- Scenario 2

## How to perform the task

1. **Preparation** - Gather inputs and validate
2. **Execution** - Apply core logic
3. **Validation** - Verify outputs
4. **Formatting** - Structure results

## Output format

```json
{
  "result": {
    "field1": "value1",
    "field2": "value2"
  }
}
```

## Best practices

- Always validate inputs before processing
- Use consistent naming conventions
- Include confidence scores when applicable
```

### Pattern 2: Domain Knowledge Skill

**Use case**: Embed domain-specific expertise
**Examples**: pdf-processor (PDF parsing best practices)

```markdown
---
name: domain-knowledge
description: Expert guidance for [domain] tasks
metadata:
  execution_mode: hybrid
  version: "1.0"
---

# Domain Knowledge

## When to use this skill

Apply this skill when working with [domain] tasks:
- Task type 1
- Task type 2

## Core concepts

### Concept 1
Explanation of key domain concept...

### Concept 2
How this applies to the task...

## Methodology

1. **Step 1**: Domain-specific action
2. **Step 2**: Apply domain rules
3. **Step 3**: Validate against domain constraints

## Edge cases

Handle these special scenarios:
- Edge case 1: Solution
- Edge case 2: Solution

## Tools and techniques

- Tool 1: When to use
- Tool 2: When to use
```

### Pattern 3: Output Format Skill

**Use case**: Standardize output structures
**Examples**: json-formatter, report-generator

```markdown
---
name: output-format
description: Standardize outputs for [use case]
metadata:
  execution_mode: llm-prompt
  version: "1.0"
---

# Output Format Skill

## When to use this skill

Use when you need to format outputs for:
- Use case 1
- Use case 2

## Required structure

All outputs must include:

```json
{
  "metadata": {
    "timestamp": "ISO-8601",
    "version": "1.0"
  },
  "data": {
    // Your content here
  },
  "status": "success|error"
}
```

## Field specifications

- `metadata.timestamp`: ISO-8601 format (e.g., "2024-01-15T10:30:00Z")
- `metadata.version`: Semantic version
- `data`: Flexible content structure
- `status`: Execution status

## Examples

### Example 1: Success response
```json
{
  "metadata": {"timestamp": "2024-01-15T10:30:00Z", "version": "1.0"},
  "data": {"result": "processed"},
  "status": "success"
}
```

### Example 2: Error response
```json
{
  "metadata": {"timestamp": "2024-01-15T10:30:00Z", "version": "1.0"},
  "data": {"error": "Invalid input"},
  "status": "error"
}
```
```

## Skill Usage

### In Agents

Skills are automatically loaded and injected into agent system prompts during execution.

**Example agent using skill:**
```yaml
# repo/agents/my-agent/agent.yaml
agent:
  name: my-agent
  description: Agent using text-summarizer skill

instructions:
  system_prompt: |
    You have access to the text-summarizer skill.
    When asked to summarize text, apply the skill methodology.
    
    [Skill instructions are auto-injected here]
```

### Direct Invocation

AI assistants (like GitHub Copilot) use skills to guide their responses:

```
User: "Summarize this article for me"
AI: [Loads text-summarizer skill]
    [Applies step-by-step methodology from skill]
    [Returns formatted output as specified]
```

## Creating Skills

### 1. Plan Skill Structure

Define:
- **Scope**: What does this skill cover?
- **Audience**: Who uses it (humans, LLMs, both)?
- **Execution mode**: Pure prompt, code, or hybrid?
- **Output**: What format is expected?

### 2. Write SKILL.md

Create `repo/skills/my-skill/SKILL.md`:

```markdown
---
name: my-skill
description: Clear description of when to use this skill
metadata:
  execution_mode: llm-prompt
  author: your-name
  version: "1.0"
---

# My Skill

## When to use this skill

Specific scenarios...

## How to perform the task

1. **Step 1** - Detailed instructions
2. **Step 2** - More instructions

## Output format

Expected structure...
```

### 3. Test Skill

**Option 1**: Via agent
- Create test agent that uses the skill
- Run: `hubbers agent run test-agent --input '...'`

**Option 2**: Manual validation
- Review skill content for clarity
- Ensure examples are accurate
- Verify output format is valid JSON Schema

### 4. Document Usage

Add examples showing:
- When to apply the skill
- Sample inputs and outputs
- Common patterns

## Best Practices

### Writing Effective Skills

1. **Be specific**: Avoid vague instructions
   - ❌ "Process the data"
   - ✅ "Extract key-value pairs from JSON, validate types, filter nulls"

2. **Use imperative voice**: Commands, not suggestions
   - ❌ "You might want to check..."
   - ✅ "Check the input for..."

3. **Include examples**: Show don't just tell
   - Always provide concrete input/output samples
   - Cover edge cases

4. **Structure output**: Define exact format
   - Use JSON schemas
   - Specify field names and types
   - Include required vs optional fields

5. **Anticipate errors**: Describe error handling
   - What to do when inputs are invalid
   - How to handle missing data
   - Fallback strategies

### Skill Organization

1. **One skill, one purpose**: Don't combine unrelated tasks
2. **Reusable**: Design for multiple agents/contexts
3. **Versioned**: Update version when changing methodology
4. **Documented**: Explain *why* not just *what*

### Execution Modes

Choose the right mode:

- **llm-prompt**: Pure instructions (no code execution)
  - Use for: Analysis, summarization, formatting
  - Fast, no dependencies
  
- **code-execution**: Invoke tools during execution
  - Use for: File I/O, API calls, complex calculations
  - Requires tool integration
  
- **hybrid**: Mix of both
  - Use for: Multi-step workflows with reasoning + actions
  - Most flexible but complex

## Skills Examples

See existing skills for reference:

### text-summarizer

**Purpose**: Summarize text content
**Execution mode**: llm-prompt
**Key sections**:
- When to use: Article/document summarization scenarios
- Methodology: 5-step process (read, identify, extract, write, validate)
- Output format: JSON with topic, keyPoints, conclusion
- Best practices: Conciseness, accuracy, structure

### pdf-processor

**Purpose**: Extract and process PDF content
**Execution mode**: hybrid
**Key sections**:
- When to use: PDF parsing and data extraction
- Methodology: Tool-based extraction + LLM analysis
- Output format: Structured text with metadata
- Tools: pdf.extract integration

### data-analyzer

**Purpose**: Analyze datasets and generate insights
**Execution mode**: hybrid
**Key sections**:
- When to use: Data analysis tasks
- Methodology: Load, clean, analyze, visualize, report
- Output format: Statistical summary + insights
- Best practices: Validate data, handle outliers, report confidence

## Troubleshooting

### Skill Not Loading

- Check file name: `SKILL.md` (case-sensitive)
- Verify directory: `repo/skills/<name>/SKILL.md`
- Validate YAML frontmatter syntax (use YAML validator)

### Instructions Unclear

- Add more examples
- Break down complex steps
- Use numbered lists for sequences
- Include "what not to do" sections

### Inconsistent Results

- Make instructions more specific
- Add output validation rules
- Include confidence thresholds
- Provide decision trees for edge cases

### Skill Not Applied

When agents don't use skills correctly:
- Verify skill name matches reference
- Check skill description is clear about when to use
- Ensure system prompt mentions skill availability
- Consider adding explicit skill invocation trigger

## Advanced Topics

### Skill Composition

Combine multiple skills in one agent:

```yaml
# agent.yaml
instructions:
  system_prompt: |
    You have access to these skills:
    - text-summarizer: For text condensation
    - data-analyzer: For statistical analysis
    
    Apply the appropriate skill based on the input type.
```

### Dynamic Skill Selection

Let agents choose skills:

```yaml
instructions:
  system_prompt: |
    Available skills: ${available_skills}
    
    1. Analyze the request
    2. Select appropriate skill(s)
    3. Apply skill methodology
    4. Return formatted result
```

### Skill Versioning

When updating skills:
1. Increment version in frontmatter
2. Document changes in skill body
3. Test with existing agents
4. Update agent manifests if needed

## Related Documentation

- [Agent Development Guide](../agents/AGENTS.md) - How agents use skills
- [Root AGENTS.md](../../AGENTS.md) - Project setup and architecture
- VS Code agents.md spec: https://agents.md/
