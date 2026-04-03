package org.hubbers.config;

public class AppConfig {
    private String repoRoot;
    private OpenAiConfig openai;
    private OllamaConfig ollama;
    private ToolsConfig tools;
    private ExecutionsConfig executions;

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public OpenAiConfig getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiConfig openai) {
        this.openai = openai;
    }

    public OllamaConfig getOllama() {
        return ollama;
    }

    public void setOllama(OllamaConfig ollama) {
        this.ollama = ollama;
    }

    public ToolsConfig getTools() {
        return tools;
    }

    public void setTools(ToolsConfig tools) {
        this.tools = tools;
    }

    public ExecutionsConfig getExecutions() {
        return executions;
    }

    public void setExecutions(ExecutionsConfig executions) {
        this.executions = executions;
    }
}
