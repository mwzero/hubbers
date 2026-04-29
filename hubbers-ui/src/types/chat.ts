export interface ToolCallTrace {
  toolName: string;
  input: any;
  output: any;
  durationMs: number;
  success: boolean;
  error?: string;
}

export interface SkillInvocationTrace {
  skillName: string;
  executionMode: string; // "llm-prompt", "script", "hybrid"
  durationMs: number;
  success: boolean;
  error?: string;
}

export interface AgentIterationTrace {
  iterationNumber: number;
  reasoning: string;
  toolCalls: ToolCallTrace[];
  result?: any;
  durationMs: number;
  isComplete: boolean;
}

export interface PipelineStepTrace {
  stepNumber: number;
  stepName: string;
  artifactType: string; // "agent", "tool", "pipeline", "skill"
  artifactName: string;
  status: 'SUCCESS' | 'FAILED' | 'PAUSED' | 'UNKNOWN';
  input?: any;
  output?: any;
  startTime: number;
  endTime: number;
  durationMs: number;
  error?: string;
}

export interface ExecutionTrace {
  executionType: string; // "agent", "pipeline", "tool", "skill"
  pipelineSteps: PipelineStepTrace[];
  iterations: AgentIterationTrace[];
  skillInvocations: SkillInvocationTrace[];
  totalIterations: number;
  totalSteps: number;
}

export interface TaskExecutionResult {
  result: any;
  reasoning: string;
  toolsUsed: string[];
  iterations: number;
  status: 'SUCCESS' | 'FAILED' | 'ERROR';
  conversationId: string;
  executionTime?: number;
  executionTrace?: ExecutionTrace;
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
  executionTrace?: ExecutionTrace;
  isLoading?: boolean;
  error?: string;
}

export interface SystemInfo {
  availableTools: number;
  agentName: string;
}

export interface ModelInfo {
  provider: string;
  name: string;
  size: number;
}

export interface ConversationSummary {
  id: string;
}

export interface StreamEvent {
  type: 'started' | 'result' | 'done' | 'error';
  data: any;
}
