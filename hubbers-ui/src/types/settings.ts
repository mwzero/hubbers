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

export interface AzureOpenAiConfig {
  endpoint: string;
  apiKey: string;
  deployment: string;
  apiVersion: string;
  defaultModel: string;
}

export interface AnthropicConfig {
  apiKey: string;
  baseUrl: string;
  defaultModel: string;
  apiVersion: string;
  maxTokens: number;
}

export interface LlamaCppConfig {
  baseUrl: string;
  apiKey?: string;
  defaultModel: string;
  timeoutSeconds?: number;
}

export interface SecurityConfig {
  allowedTools?: string[];
  deniedTools?: string[];
  apiKey?: string;
  allowedCommands?: string[];
}

export interface VectorDbConfig {
  enabled?: boolean;
  provider: string;
  rootPath: string;
  defaultIndex: string;
  embeddingStrategy: string;
  dimensions: number;
  defaultTopK: number;
  certifiedOnly?: boolean;
  retentionDays?: number;
  allowedPaths?: string[];
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
  azureOpenai?: AzureOpenAiConfig;
  anthropic?: AnthropicConfig;
  llamaCpp?: LlamaCppConfig;
  vectorDb?: VectorDbConfig;
  tools: ToolsConfig;
  executions: ExecutionsConfig;
  security?: SecurityConfig;
}
