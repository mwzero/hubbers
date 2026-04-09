# UI Skills Integration Guide

**Documento per Agent Vibe Coding**

Questo documento contiene tutte le informazioni necessarie per integrare la gestione degli **Skills** nella UI di Hubbers.

---

## 📌 Overview

Hubbers ha 4 tipi di artifacts:
- **Agents** - AI agents con ReAct loop
- **Tools** - Operazioni atomiche (fetch RSS, file ops, CSV, etc.)
- **Pipelines** - Workflow multi-step pre-definiti
- **Skills** ⭐ **NUOVO** - Metodologie riusabili (sentiment analysis, NER, translation, etc.)

Gli Skills seguono la specifica [agentskills.io](https://agentskills.io) e sono memorizzati come file `SKILL.md` con:
- **Metadata YAML** (frontmatter): nome, versione, descrizione, modello
- **Instructions Markdown**: prompt/metodologia riusabile

---

## 🔌 API REST per Skills

### **1. GET /api/skills**
Lista tutti gli skills disponibili.

**Request:**
```http
GET /api/skills
```

**Response:**
```json
{
  "items": [
    "sentiment-analysis",
    "ner-extraction",
    "translation",
    "text-summarizer",
    "data-analyzer",
    "pdf-processor"
  ]
}
```

---

### **2. GET /api/manifest/skills/{name}**
Leggi il manifest SKILL.md completo.

**Request:**
```http
GET /api/manifest/skills/sentiment-analysis
```

**Response:** (Content-Type: `text/yaml; charset=utf-8`)
```markdown
## Metadata

```json
{
  "name": "sentiment-analysis",
  "version": "1.0.0",
  "description": "Analyze sentiment of text and classify as positive, negative, or neutral with confidence scores",
  "tags": ["sentiment", "nlp", "classification"],
  "author": "Hubbers Team",
  "provider": "ollama",
  "temperature": 0.2
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

You are a sentiment analysis expert. Analyze the provided text and classify...
[rest of markdown instructions]
```

---

### **3. PUT /api/manifest/skills/{name}**
Salva/aggiorna un manifest SKILL.md.

**Request:**
```http
PUT /api/manifest/skills/my-new-skill
Content-Type: text/plain

## Metadata
```json
{
  "name": "my-new-skill",
  ...
}
```

## Instructions
[your instructions here]
```

**Response:**
```json
{
  "saved": true
}
```

**Response (validation error):**
```json
{
  "saved": false,
  "errors": [
    "Skill name missing",
    "Skill instructions missing"
  ]
}
```

---

### **4. POST /api/validate/skills**
Valida un manifest SKILL.md prima del salvataggio.

**Request:**
```http
POST /api/validate/skills
Content-Type: text/plain

[SKILL.md content]
```

**Response:**
```json
{
  "valid": true,
  "errors": []
}
```

**Response (con errori):**
```json
{
  "valid": false,
  "errors": [
    "Skill metadata missing",
    "Skill instructions missing"
  ]
}
```

---

### **5. POST /api/run/skills/{name}**
Esegui uno skill con input JSON.

**Request:**
```http
POST /api/run/skills/sentiment-analysis
Content-Type: application/json

{
  "text": "This product is absolutely amazing! I love it!"
}
```

**Response (success):**
```json
{
  "executionId": "20260409_160230-abc123",
  "status": "SUCCESS",
  "output": {
    "sentiment": "positive",
    "confidence": 0.95,
    "explanation": "The text contains strong positive indicators..."
  },
  "error": null,
  "metadata": {
    "startedAt": 1712677350123,
    "endedAt": 1712677352456,
    "durationMs": 2333,
    "model": "qwen2.5-coder:7b"
  }
}
```

**Response (error):**
```json
{
  "executionId": "20260409_160230-abc123",
  "status": "FAILED",
  "output": null,
  "error": "Invalid input: text field required",
  "metadata": {...}
}
```

---

## 🎨 UI Components da Implementare

### **1. Skills List Page**
Simile alla pagina Agents/Tools/Pipelines, mostra:
- Card per ogni skill con:
  - 📝 Nome skill
  - 📄 Descrizione
  - 🏷️ Tags (se presenti nel metadata)
  - ▶️ Bottone "Run"
  - ✏️ Bottone "Edit"

### **2. Skill Detail/Editor Page**
Split view con:
- **Left Panel**: Editor YAML/Markdown per SKILL.md
  - Syntax highlighting per sezioni `## Metadata`, `## Model`, `## Instructions`
  - Validazione real-time
- **Right Panel**: Preview renderizzato del Markdown

### **3. Skill Run Dialog**
Form dinamico basato su `input` schema dello skill (se presente):
- Input JSON editor con schema validation
- Bottone "Execute"
- Real-time output display
- Link a execution history

### **4. Skills Tab nella Navigation**
Aggiungere tab "Skills" accanto a:
- Agents
- Tools
- Pipelines
- **Skills** ⭐ (nuovo)
- Executions

---

## 📦 Struttura Dati Skill

### **SkillManifest (Java)**
```java
public class SkillManifest {
    private SkillFrontmatter frontmatter;  // Metadata YAML
    private String body;                   // Instructions Markdown
    private Path skillPath;
    private List<Path> scripts;           // Optional: scripts/ directory
    private List<Path> references;        // Optional: references/ docs
    private List<Path> assets;            // Optional: assets/ templates
}
```

### **SkillFrontmatter (metadata)**
```java
public class SkillFrontmatter {
    private String name;                  // Required
    private String version;               // Required
    private String description;           // Required
    private List<String> tags;            // Optional
    private String author;                // Optional
    private String provider;              // Optional (ollama, openai)
    private Double temperature;           // Optional (0.0-1.0)
    private JsonNode input;               // Optional: JSON Schema
    private JsonNode output;              // Optional: JSON Schema
}
```

---

## 🔄 Differenze con Agents/Tools/Pipelines

| Aspetto | Agent | Tool | Pipeline | **Skill** |
|---------|-------|------|----------|-----------|
| **File** | `AGENT.md` | `tool.yaml` | `pipeline.yaml` | `SKILL.md` |
| **Execution** | ReAct loop + tools | Driver Java | Step sequence | LLM prompt |
| **Forms** | ✅ Supportato | ✅ Supportato | ✅ Supportato | ❌ No forms |
| **Model Config** | ✅ Embedded | ❌ N/A | ❌ N/A | ✅ Embedded |
| **Reusable** | ❌ Standalone | ❌ Standalone | ❌ Standalone | ✅ Injected in agents |

**Key Point**: Gli skills sono **metodologie riusabili** che vengono iniettate dentro gli agents (vedi `universal.task` agent che usa RAG filtering per selezionare skills rilevanti).

---

## 🚀 Integration Checklist

### **Backend (✅ Già implementato)**
- ✅ `ManifestType.SKILL` enum
- ✅ `GET /api/skills`
- ✅ `GET /api/manifest/skills/{name}`
- ✅ `PUT /api/manifest/skills/{name}`
- ✅ `POST /api/validate/skills`
- ✅ `POST /api/run/skills/{name}`
- ✅ `ManifestValidator.validateSkill()`
- ✅ `RuntimeFacade.runSkill()`

### **Frontend (🔨 Da implementare)**
- ❌ Skills navigation tab
- ❌ Skills list page with cards
- ❌ Skill detail/editor page
- ❌ Skill run dialog with JSON input
- ❌ Skills API client methods
- ❌ Skills state management (Redux/Zustand/Context)
- ❌ Link executions to skills

---

## 💡 Esempi di Skills Esistenti

Questi sono i 6 skills già disponibili nel sistema:

1. **sentiment-analysis** - Classifica sentiment (positive/negative/neutral)
2. **ner-extraction** - Named Entity Recognition (persone, luoghi, organizzazioni)
3. **translation** - Traduzione multilingua
4. **text-summarizer** - Riassunto testi lunghi
5. **data-analyzer** - Analisi statistica dati strutturati
6. **pdf-processor** - Estrazione e processing PDF

---

## 🎯 User Flow Tipico

1. **User naviga alla tab "Skills"**
2. **Vede lista di 6 skills** con descrizioni
3. **Click su "sentiment-analysis"** → apre detail page
4. **Vede SKILL.md content** in editor
5. **Click "Run"** → apre dialog con JSON input
6. **Inserisce**: `{"text": "Great product!"}`
7. **Click "Execute"** → API POST `/api/run/skills/sentiment-analysis`
8. **Vede output**: `{"sentiment": "positive", "confidence": 0.95}`
9. **Può vedere execution history** per questo skill

---

## 🔧 Considerazioni Tecniche

### **Parsing SKILL.md nel Frontend**
Il file ha formato:
```markdown
## Metadata
```json
{...}
```

## Model  
```json
{...}
```

## Instructions
[markdown content]
```

Suggerisco di:
1. Splittare per `## ` headers
2. Estrarre blocchi ```json``` con regex
3. Parsare JSON separatamente
4. Renderizzare ## Instructions con markdown renderer (es. `react-markdown`)

### **Validation Real-Time**
Chiamare `POST /api/validate/skills` on blur o con debounce mentre l'utente edita.

### **Monaco Editor**
Per editing SKILL.md, considera Monaco Editor con:
- Syntax highlighting Markdown + JSON
- Schema validation inline
- Auto-completion per metadata fields

---

## 📞 API Testing

Per testare le API manualmente:

```bash
# List skills
curl http://localhost:7070/api/skills

# Get skill manifest
curl http://localhost:7070/api/manifest/skills/sentiment-analysis

# Run skill
curl -X POST http://localhost:7070/api/run/skills/sentiment-analysis \
  -H "Content-Type: application/json" \
  -d '{"text":"Amazing product!"}'

# Validate skill
curl -X POST http://localhost:7070/api/validate/skills \
  -H "Content-Type: text/plain" \
  --data-binary @new-skill.md
```

---

## 🎨 Design Notes

**Colori suggeriti per Skills:**
- 🟣 Purple/Violet theme (per distinguerli da Agents=blue, Tools=green, Pipelines=orange)
- Icon: 📚 o 🧠 o ⚡

**Layout:**
- Mantieni consistenza con pagine Agents/Tools/Pipelines esistenti
- Usa stesso grid layout per le cards
- Stessa struttura detail page (tabs: Overview, Manifest, Run, History)

---

## 🐛 Known Issues

- ⚠️ Gli skills non supportano forms (diversamente da agents/tools/pipelines)
- ⚠️ Il formato `SKILL.md` è case-sensitive (deve essere maiuscolo)
- ⚠️ Skills possono avere directory opzionali: `scripts/`, `references/`, `assets/` (da gestire in futuro)

---

## 📚 References

- Specifica agentskills.io: https://agentskills.io
- Skills esistenti: `repo/skills/*/SKILL.md`
- Backend code: `src/main/java/org/hubbers/skill/`
- Web API: `src/main/java/org/hubbers/web/WebServer.java`

---

**Good luck! 🚀**
