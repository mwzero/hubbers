export interface OllamaConfig {
  baseUrl: string;
  defaultModel: string;
  timeoutSeconds?: number;
}

export interface OpenAiConfig {
  apiKey: string;
  baseUrl: string;
  defaultModel: string;
}

export interface ToolsConfig {
  tools: Record<string, Record<string, string>>;
}

export interface ExecutionsConfig {
  path: string;
  retentionDays: number;
  maxConcurrent: number;
}

export interface AppConfig {
  repoRoot: string;
  openai: OpenAiConfig;
  ollama: OllamaConfig;
  tools: ToolsConfig;
  executions: ExecutionsConfig;
}
