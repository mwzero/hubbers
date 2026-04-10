## Metadata

```json
{
  "name": "ner-extraction",
  "description": "Extract named entities (people, organizations, locations, products, technologies) from text. Use for information extraction, document processing, or knowledge graph construction.",
  "executionMode": "llm-prompt",
  "author": "hubbers-team",
  "version": "1.0"
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

## Instructions

# Named Entity Recognition (NER) Skill

## When to use this skill

Use this skill when you need to:
- Extract people, organizations, locations from text
- Identify products, brands, or technologies mentioned
- Build structured data from unstructured text
- Create knowledge graphs or entity databases

## Entity types to extract

Extract these entity types:

1. **PERSON** (PER): Names of people
2. **ORGANIZATION** (ORG): Companies, institutions, agencies
3. **LOCATION** (LOC): Cities, countries, regions, addresses
4. **PRODUCT** (PROD): Product names, brands
5. **TECHNOLOGY** (TECH): Software, frameworks, programming languages

## How to extract entities

1. **Read the text carefully** - Identify all proper nouns and key terms
2. **Classify each entity** - Assign the appropriate type
3. **Include context** - Note what the entity is doing or being described as
4. **Normalize names** - Use consistent formatting (e.g., "New York" not "NYC")
5. **Resolve ambiguity** - If unsure, include it with lower confidence

## Output format

Return a JSON object with this structure:

```json
{
  "entities": [
    {
      "text": "OpenAI",
      "type": "ORGANIZATION",
      "confidence": 0.99,
      "context": "AI research company mentioned as creator"
    },
    {
      "text": "GPT-4",
      "type": "TECHNOLOGY",
      "confidence": 0.95,
      "context": "Language model discussed in article"
    }
  ],
  "summary": {
    "total": 10,
    "by_type": {
      "PERSON": 2,
      "ORGANIZATION": 3,
      "TECHNOLOGY": 5
    }
  }
}
```

## Guidelines

- **Precision over recall**: Only extract clear entities, don't guess
- **Context helps**: Include brief context about why/how entity appears
- **Handle ambiguity**: "Paris" could be city or person - use context
- **Confidence scoring**: Lower confidence for ambiguous or unclear entities
- **Normalization**: Use full names ("United States" not "US")

## Example

Input:
```json
{
  "text": "Elon Musk announced that Tesla will open a new factory in Berlin, Germany. The facility will produce electric vehicles using advanced AI technology."
}
```

Output:
```json
{
  "entities": [
    {
      "text": "Elon Musk",
      "type": "PERSON",
      "confidence": 0.99,
      "context": "CEO making announcement about new factory"
    },
    {
      "text": "Tesla",
      "type": "ORGANIZATION",
      "confidence": 0.99,
      "context": "Company opening new manufacturing facility"
    },
    {
      "text": "Berlin",
      "type": "LOCATION",
      "confidence": 0.99,
      "context": "City location of new factory"
    },
    {
      "text": "Germany",
      "type": "LOCATION",
      "confidence": 0.99,
      "context": "Country where Berlin is located"
    },
    {
      "text": "AI",
      "type": "TECHNOLOGY",
      "confidence": 0.95,
      "context": "Technology used in vehicle production"
    }
  ],
  "summary": {
    "total": 5,
    "by_type": {
      "PERSON": 1,
      "ORGANIZATION": 1,
      "LOCATION": 2,
      "TECHNOLOGY": 1
    }
  }
}
```
