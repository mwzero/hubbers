---
title: Skills Guide
---

# Skills Guide

Skills are reusable methodologies stored as `SKILL.md` files with structured sections. They are lighter-weight than agents and work well for prompting patterns, hybrid workflows, and domain-specific instructions.

## Current Format

The bundled repo uses Markdown sections with JSON code blocks, not YAML frontmatter.

Typical sections:

- `## Metadata`
- `## Model`
- `## Instructions`

Representative examples:

- [hubbers-repo/src/main/resources/repo/skills/sentiment-analysis/SKILL.md](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/skills/sentiment-analysis/SKILL.md:1)
- [hubbers-repo/src/main/resources/repo/skills/pdf-processor/SKILL.md](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/skills/pdf-processor/SKILL.md:1)

## Minimal Example

````markdown
## Metadata

```json
{
  "name": "sentiment-analysis",
  "description": "Analyze sentiment of text with confidence scores",
  "executionMode": "llm-prompt",
  "author": "hubbers-team",
  "version": "1.0"
}
```

## Model

```json
{
  "provider": "ollama",
  "name": "qwen3:4b",
  "temperature": 0.2
}
```

## Instructions

Describe when to use the skill, how to apply it, and the required output format.
````

## Execution Modes Seen In The Repo

- `llm-prompt`
- `hybrid`

Hybrid skills can include helper scripts and reference documents alongside the `SKILL.md` file.

Example:

- [hubbers-repo/src/main/resources/repo/skills/pdf-processor/scripts/extract_text.py](/Users/mauriziofarina/src/hubbers/hubbers-repo/src/main/resources/repo/skills/pdf-processor/scripts/extract_text.py:1)

## When To Use A Skill Instead Of An Agent

Prefer a skill when the value is primarily:

- reusable methodology
- domain guidance
- structured prompting
- a hybrid instruction-plus-script pattern

Prefer an agent when the artifact needs autonomous orchestration, tool calling, or conversation support.

## Current Bundled Skills

- `sentiment-analysis`
- `ner-extraction`
- `translation`
- `text-summarizer`
- `data-analyzer`
- `pdf-processor`

## Testing

```bash
java -jar hubbers-distribution/target/hubbers.jar list skills
java -jar hubbers-distribution/target/hubbers.jar skill validate sentiment-analysis
```

## Related Docs

- [Agents Guide](AGENTS.md)
- [Software Architecture](SWA.md)
