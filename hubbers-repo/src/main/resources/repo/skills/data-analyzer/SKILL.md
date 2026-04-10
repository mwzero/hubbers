## Metadata

```json
{
  "name": "data-analyzer",
  "description": "Analyze CSV or JSON datasets with statistical analysis and insights. Includes Python script for advanced processing.",
  "executionMode": "hybrid",
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

# Data Analyzer Skill

## When to use this skill

Use this skill when you need to:
- Analyze CSV or JSON datasets
- Calculate statistics (mean, median, mode, std dev)
- Find patterns and correlations
- Generate data insights

## How it works

This skill executes a Python script that:
1. Loads the data from input
2. Performs statistical analysis
3. Identifies patterns and outliers
4. Returns structured insights

## Input format

```json
{
  "data": [
    {"name": "Item1", "value": 100, "category": "A"},
    {"name": "Item2", "value": 150, "category": "B"}
  ],
  "analysis_type": "summary|correlation|outliers"
}
```

## Output format

```json
{
  "statistics": {
    "count": 100,
    "mean": 125.5,
    "median": 120,
    "std_dev": 25.3
  },
  "insights": [
    "Strong positive correlation between value and category A",
    "3 outliers detected with values > 2 standard deviations"
  ]
}
```

## Script details

The `scripts/analyze.py` script handles all data processing using pandas and numpy for efficient computation.
