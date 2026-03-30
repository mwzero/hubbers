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

List artifacts

```
hubbers list agents
hubbers list tools
hubbers list pipelines
```

Run an agent
```
hubbers agent run text.summarizer --input examples/text.json
```

Run a tool
```
hubbers tool run pdf.extract --input examples/input.json
```

Run a pipeline
```
hubbers pipeline run pdf.summary --input examples/input.json
```

## 🛠️ Installation

Requirements: 

- Java 21+
- Maven 3.9+
- Docker (for docker-based tools)
- OpenAI API key

Build
```
mvn clean package
```

Run
```
java -jar target/hubbers-runtime.jar
```

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
