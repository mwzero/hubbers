export interface TaskExecutionResult {
  result: any;
  reasoning: string;
  toolsUsed: string[];
  iterations: number;
  status: 'SUCCESS' | 'FAILED' | 'ERROR';
  conversationId: string;
  executionTime?: number;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'agent';
  content: string;
  timestamp: Date;
  // Agent-specific fields
  result?: any;
  reasoning?: string;
  toolsUsed?: string[];
  iterations?: number;
  status?: 'SUCCESS' | 'FAILED' | 'ERROR';
  conversationId?: string;
  executionTime?: number;
  isLoading?: boolean;
  error?: string;
}

export interface SystemInfo {
  availableTools: number;
  agentName: string;
}
