## Metadata

```json
{
  "name": "text-summarizer",
  "description": "Summarize text content into concise, informative summaries. Use when the user needs to condense articles, documents, or long text passages.",
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
  "temperature": 0.3
}
```

## Instructions

# Text Summarizer Skill

## When to use this skill

Use this skill when you need to:
- Summarize articles, blog posts, or news stories
- Condense long documents into key points
- Extract main ideas from text passages
- Create executive summaries

## How to summarize text

1. **Read the input text carefully** - Identify the main topic, key arguments, and supporting evidence
2. **Identify core messages** - Focus on what the author wants to communicate
3. **Extract key points** - Find the most important facts, statistics, and conclusions
4. **Write concisely** - Use clear, direct language without unnecessary details
5. **Maintain accuracy** - Don't add information that isn't in the original text

## Summary structure

Your summary should include:

- **Main topic**: What is the text about?
- **Key points**: 3-5 most important ideas (bullet points)
- **Conclusion**: The main takeaway or conclusion

## Output format

Return a JSON object with this structure:

```json
{
  "summary": {
    "topic": "Brief description of main topic",
    "keyPoints": [
      "First key point",
      "Second key point",
      "Third key point"
    ],
    "conclusion": "Main takeaway or conclusion",
    "wordCount": 150
  }
}
```

## Guidelines

- **Length**: Aim for 100-200 words unless specified otherwise
- **Objectivity**: Stay neutral, don't inject personal opinions
- **Accuracy**: Only include information from the source text
- **Clarity**: Use simple, clear language
- **Completeness**: Capture all major points

## Example

Input:
```json
{
  "text": "Climate change is accelerating faster than previously predicted. Recent studies show global temperatures have risen 1.2°C since pre-industrial times. Scientists warn that without immediate action, we risk crossing irreversible tipping points. Renewable energy adoption has grown 20% annually, but fossil fuel consumption remains high."
}
```

Output:
```json
{
  "summary": {
    "topic": "Accelerating climate change and energy transition challenges",
    "keyPoints": [
      "Global temperatures have risen 1.2°C since pre-industrial era",
      "Climate change progressing faster than predictions",
      "Risk of crossing irreversible tipping points without action",
      "Renewable energy growing 20% annually but fossil fuels still dominant"
    ],
    "conclusion": "Urgent action needed as climate change accelerates despite renewable energy growth",
    "wordCount": 52
  }
}
```
