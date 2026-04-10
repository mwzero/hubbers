## Metadata

```json
{
  "name": "translation",
  "description": "Translate text between languages with cultural adaptation. Use for multilingual content, localization, or international communication.",
  "executionMode": "llm-prompt",
  "author": "hubbers-team",
  "version": "1.0"
}
```

## Model

```json
{
  "provider": "ollama",
  "name": "gemma4",
  "temperature": 0.3
}
```

## Instructions

# Translation Skill

## When to use this skill

Use this skill when you need to:
- Translate text between languages
- Localize content for different regions
- Adapt cultural references and idioms
- Preserve tone and style in translation

## How to translate effectively

1. **Understand source text** - Grasp meaning, tone, and intent
2. **Know the audience** - Consider target culture and context
3. **Preserve meaning** - Don't translate word-by-word, convey the idea
4. **Adapt culturally** - Replace idioms/references with local equivalents
5. **Maintain tone** - Formal text stays formal, casual stays casual

## Output format

Return a JSON object with this structure:

```json
{
  "translation": "The translated text in target language",
  "source_language": "en",
  "target_language": "es",
  "confidence": 0.95,
  "notes": "Any cultural adaptations or translation choices explained"
}
```

## Translation principles

### Accuracy
- Preserve the original meaning
- Don't add or remove information
- Maintain technical terms appropriately

### Naturalness
- Sound natural in target language
- Use native phrasing patterns
- Avoid literal word-for-word translation

### Cultural adaptation
- Replace culture-specific references
- Adapt idioms and expressions
- Consider local customs and sensitivities

### Tone and style
- Match formality level
- Preserve emotional tone
- Keep the author's voice

## Guidelines

- **Context is crucial**: "Bank" (financial institution vs. river bank)
- **Idioms need adaptation**: "It's raining cats and dogs" → target language equivalent
- **Honorifics matter**: Formal/informal "you" in many languages
- **Numbers and dates**: Consider local formatting (DD/MM vs MM/DD)
- **Units**: Convert measurements if needed (miles → kilometers)

## Example

Input:
```json
{
  "text": "The early bird catches the worm. Our new product launches at 9 AM EST on 12/25/2024.",
  "target_language": "es"
}
```

Output:
```json
{
  "translation": "Al que madruga, Dios le ayuda. Nuestro nuevo producto se lanza a las 9 AM EST el 25/12/2024.",
  "source_language": "en",
  "target_language": "es",
  "confidence": 0.92,
  "notes": "Adapted English idiom 'early bird catches the worm' to Spanish equivalent 'Al que madruga, Dios le ayuda' (God helps those who rise early). Changed date format from MM/DD/YYYY to DD/MM/YYYY as is standard in Spanish-speaking countries."
}
```
