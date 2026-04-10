## Metadata

```json
{
  "name": "sentiment-analysis",
  "description": "Analyze sentiment (positive/negative/neutral) of text with confidence scores. Use for social media analysis, review processing, or customer feedback.",
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
  "temperature": 0.2
}
```

## Instructions

# Sentiment Analysis Skill

## When to use this skill

Use this skill when you need to:
- Analyze sentiment in customer reviews, social media posts, or feedback
- Classify text as positive, negative, or neutral
- Extract emotional tone from written content
- Score sentiment intensity

## How to analyze sentiment

1. **Read the text carefully** - Understand the full context
2. **Identify sentiment markers**:
   - Positive: praise words, success indicators, satisfaction expressions
   - Negative: complaints, criticism, disappointment, frustration
   - Neutral: factual statements, informational content
3. **Consider context** - Sarcasm, negation, intensifiers affect sentiment
4. **Assign confidence score** - How certain are you about the classification?

## Output format

Return a JSON object with this structure:

```json
{
  "sentiment": "positive|negative|neutral",
  "confidence": 0.95,
  "score": 0.8,
  "reasoning": "Brief explanation of why this sentiment was assigned",
  "keywords": ["key", "sentiment", "words"]
}
```

## Fields explanation

- **sentiment**: Overall classification (positive/negative/neutral)
- **confidence**: How certain you are (0.0 to 1.0)
- **score**: Sentiment intensity (-1.0 very negative, 0.0 neutral, +1.0 very positive)
- **reasoning**: Brief explanation of your analysis
- **keywords**: Key words that influenced the sentiment

## Guidelines

- **Context matters**: "Not bad" is mildly positive, not negative
- **Intensifiers**: "Very good" vs "good" affects score
- **Mixed sentiment**: If truly mixed, choose "neutral" and explain in reasoning
- **Confidence**: Lower confidence for ambiguous or sarcastic text

## Example

Input:
```json
{
  "text": "This product exceeded my expectations! Fast shipping and great quality. Highly recommend."
}
```

Output:
```json
{
  "sentiment": "positive",
  "confidence": 0.98,
  "score": 0.9,
  "reasoning": "Strong positive language with multiple praise indicators: 'exceeded expectations', 'great quality', 'highly recommend'",
  "keywords": ["exceeded", "expectations", "great", "quality", "recommend"]
}
```
