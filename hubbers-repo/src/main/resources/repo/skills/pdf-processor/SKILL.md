## Metadata

```json
{
  "name": "pdf-processor",
  "description": "Extract and process PDF documents. Supports text extraction, summarization, and metadata analysis. Uses hybrid execution with Python scripts.",
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

# PDF Processor Skill

## When to use this skill

Use this skill when you need to:
- Extract text content from PDF files
- Summarize PDF documents
- Analyze PDF structure (pages, metadata)
- Process single or multiple PDFs

## How it works (Hybrid Mode)

This skill operates in **hybrid mode**, meaning the LLM will decide the best approach:

1. **LLM-based processing** (for simple tasks):
   - Summarizing extracted text
   - Answering questions about PDF content
   - Formatting and restructuring text

2. **Script-based processing** (for complex tasks):
   - Extracting text from binary PDF files
   - Processing large PDFs
   - Batch processing multiple PDFs
   - Extracting images and tables

The LLM will analyze your request and choose the most efficient method.

## Input format

```json
{
  "operation": "extract|summarize|analyze",
  "pdf_path": "/path/to/document.pdf",
  "options": {
    "pages": "1-5",
    "include_metadata": true
  }
}
```

## Operations

### Extract
Extract raw text from PDF pages.

### Summarize
Create a concise summary of PDF content (uses LLM instructions).

### Analyze
Provide structure information: page count, metadata, word count.

## Output format

```json
{
  "operation": "extract",
  "result": {
    "text": "Extracted text content...",
    "page_count": 10,
    "word_count": 5420,
    "metadata": {
      "title": "Document Title",
      "author": "Author Name"
    }
  },
  "method_used": "script|llm"
}
```

## Available scripts

- `scripts/extract_text.py` - Extract text from PDF
- `scripts/get_metadata.py` - Get PDF metadata
- `scripts/split_pages.py` - Split PDF into individual pages

## Reference documentation

See `references/pdf_guide.md` for detailed PDF processing guidelines and best practices.

## Decision criteria (for LLM)

**Use scripts when:**
- Input contains a file path to a PDF
- Request involves binary PDF processing
- Large PDF files (>10 pages)
- Batch processing needed

**Use instructions when:**
- Already have extracted text
- Simple summarization or Q&A
- Formatting or restructuring text
- No file path provided
