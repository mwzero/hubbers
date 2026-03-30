# Hubbers Runtime

**Hubbers Runtime** is a lightweight, Git-native Java framework for executing **AI agents, tools, and pipelines** defined as YAML artifacts.

It transforms a simple repository into an executable system:

> **Git repository → Agent runtime**

## ✨ Key Features

- **Git-native architecture**  
  Agents, tools, and pipelines are defined as versioned YAML files.

- **Agent execution**  
  Run AI agents powered by LLMs (starting with OpenAI).

- **Tool integration**  
  Support for:
  - HTTP APIs  
  - Docker-based tools  

- **Pipeline orchestration**  
  Compose agents and tools into sequential workflows.

- **CLI-first**  
  Execute everything via a simple command-line interface.

- **Minimal dependencies**  
  Pure Java — no Spring, no heavy frameworks.

## 🧱 Core Concepts

### Agent
An agent defines how a task is executed using an LLM.

```yaml
agent:
  name: text.summarizer
  version: 1.0.0

model:
  provider: openai
  name: gpt-4.1-mini

instructions:
  system_prompt: |
    Summarize the text in Italian.

input:
  schema:
    type: object
    properties:
      text:
        type: string

output:
  schema:
    type: object
    properties:
      summary:
        type: string
```

### Tool

A tool represents an executable capability.

HTTP Tool

```yaml
tool:
  name: weather.lookup
  version: 1.0.0

type: http

config:
  base_url: https://api.example.com/weather
  method: POST

Docker Tool

tool:
  name: pdf.extract
  version: 1.0.0

type: docker

config:
  image: myorg/pdf-extract:1.0.0
```

### Storage Tool (Lucene Key-Value)

A key-value store using Lucene as NoSQL database.

```yaml
tool:
  name: lucene.kv
  version: 1.0.0

type: lucene.kv

config:
  index_path: ./datasets/lucene/kv-store
```

### Browser Automation (Pinchtab)

Browser control and web scraping using Pinchtab.

```yaml
tool:
  name: browser.pinchtab
  version: 1.0.0

type: browser.pinchtab

config:
  pinchtab_url: http://localhost:9867
  profiles:
    default:
      name: default
      mode: headless
    authenticated:
      name: work-profile
      mode: headed
```

### Pipeline

A pipeline composes agents and tools into workflows.

```yaml
pipeline:
  name: pdf.summary

steps:
  - id: extract
    tool: pdf.extract

  - id: summarize
    agent: text.summarizer
    input_mapping:
      text: ${steps.extract.output.text}
```

## 📁 Repository Structure

repo/
  agents/
    text.summarizer/
      agent.yaml
  tools/
    pdf.extract/
      tool.yaml
  pipelines/
    pdf.summary/
      pipeline.yaml
  examples/

## ⚙️ CLI Usage

### General Options

```
hubbers --help                  # Show help
hubbers --version              # Show version
```

### List Artifacts

```
hubbers list agents            # List all available agents
hubbers list tools             # List all available tools
hubbers list pipelines         # List all available pipelines
```

### Run Agent

```
hubbers agent run <name> --input <file.json>
hubbers agent run <name> --input '<json-string>'
```

Examples:
```
# Using a file
hubbers agent run text.summarizer --input examples/text.json

# Using direct JSON
hubbers agent run text.summarizer --input '{"text":"Your text here"}'
```

### Run Tool

```
hubbers tool run <name> --input <file.json>
hubbers tool run <name> --input '<json-string>'
```

Examples:
```
# Using a file
hubbers tool run pdf.extract --input examples/input.json

# Using direct JSON
hubbers tool run pdf.extract --input '{"file":"document.pdf"}'

# Lucene KV store - put a record
hubbers tool run lucene.kv --input "{\"operation\":\"put\",\"key\":\"user:1001\",\"value\":{\"name\":\"Mario Rossi\",\"email\":\"mario@example.com\"}}"

# Lucene KV store - get a record
hubbers tool run lucene.kv --input "{\"operation\":\"get\",\"key\":\"user:1001\"}"

# Lucene KV store - list all keys
hubbers tool run lucene.kv --input "{\"operation\":\"list_keys\",\"limit\":10}"

# Browser automation - navigate (requires Pinchtab daemon running)
hubbers tool run browser.pinchtab --input "{\"action\":\"navigate\",\"url\":\"https://example.com\"}"

# Browser automation - get page snapshot
hubbers tool run browser.pinchtab --input "{\"action\":\"snapshot\",\"filter\":\"interactive\"}"

# Browser automation - extract text
hubbers tool run browser.pinchtab --input "{\"action\":\"extract_text\"}"
```

### Run Pipeline

```
hubbers pipeline run <name> --input <file.json>
hubbers pipeline run <name> --input '<json-string>'
```

Examples:
```
# Using a file
hubbers pipeline run pdf.summary --input examples/input.json

# Using direct JSON
hubbers pipeline run pdf.summary --input '{"file":"document.pdf"}'
```

### Web UI

Start the web user interface and API server:

```
hubbers web                    # Start on default port 7070
hubbers web --port 8080        # Start on custom port
```

The web UI will be available at `http://localhost:7070` (or the specified port).

## 🛠️ Installation

### Requirements

**For JAR Distribution:**
- Java 21+
- Maven 3.9+
- Docker (for docker-based tools)
- OpenAI API key
- Pinchtab (optional, for browser automation tools)

**For Native Executable:**
- GraalVM 21+ with native-image
- Maven 3.9+
- Docker (for docker-based tools)
- OpenAI API key
- Visual Studio Build Tools (Windows only)
- Pinchtab (optional, for browser automation tools)

### Build Options

#### Option 1: JAR Distribution (JVM Required)

Build the standard JAR:
```bash
mvn clean package
```

Run:
```bash
java -jar target/hubbers-0.1.0-SNAPSHOT.jar <command>
```

#### Option 2: Native Executable (Standalone)

Build a native executable that doesn't require JVM:

**Linux/macOS:**
```bash
./build-native.sh
```

**Windows:**
```cmd
build-native.bat
```

The native executable will be created in `target/hubbers` (or `target/hubbers.exe` on Windows).

Test it:
```bash
./target/hubbers --help
```

### Installation

#### Install Globally

To make the `hubbers` command available system-wide:

**Linux/macOS:**
```bash
# System installation (copy to /usr/local/bin)
sudo ./install.sh

# Development mode (symlink for easy updates)
./install.sh --dev
```

**Windows (as Administrator):**
```cmd
install.bat
```

After installation, the command is available directly:
```bash
hubbers --help
hubbers list agents
```

#### GraalVM Setup

If you don't have GraalVM installed:

1. Download GraalVM from: https://www.graalvm.org/downloads/
2. Set `JAVA_HOME` to the GraalVM directory
3. Install native-image:
   ```bash
   gu install native-image
   ```
4. On Windows, ensure Visual Studio Build Tools are installed

## ⚙️ Configuration

Create an application.yaml file:

```yaml
repoRoot: ./repo

openai:
  apiKey: ${OPENAI_API_KEY}
  baseUrl: https://api.openai.com/v1
  defaultModel: gpt-4.1-mini
```

Set your API key:
```
export OPENAI_API_KEY=your_key_here
```

### Pinchtab Setup (Optional)

For browser automation tools, install Pinchtab:

**macOS / Linux:**
```bash
curl -fsSL https://pinchtab.com/install.sh | bash
```

**Homebrew:**
```bash
brew install pinchtab/tap/pinchtab
```

**npm:**
```bash
npm install -g pinchtab
```

Start the Pinchtab daemon:
```bash
pinchtab daemon install
pinchtab daemon
```

The browser control server will be available at `http://localhost:9867`.

Verify installation:
```bash
pinchtab --help
```

## 🧠 Philosophy

Agents are artifacts, not hidden logic
Git is the source of truth
The runtime is transparent and controllable
No magic, no heavy frameworks

## 🎯 Vision

Hubbers Runtime is the foundation for a broader platform:

> An ecosystem where AI agents become versioned, composable, and reusable assets

## 🔮 Roadmap

- Multi-provider support (OpenAI, open-source models)
- Async execution
- Cost tracking and budgeting
- GitHub integration
- Agent marketplace

## 🚧 Status

Early stage — MVP in development.

## 🤝 Contributing

Contributions are welcome.
Feel free to open issues or submit pull requests.

## 📄 License

MIT License (or choose your preferred license)

##💡 Tagline

> Define agents, tools, and workflows as code — run them anywhere.
