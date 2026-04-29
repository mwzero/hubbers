import type { TaskExecutionResult, SystemInfo, ModelInfo, ConversationSummary } from '@/types/chat';

const BASE = '';

async function handleResponse(res: Response) {
  if (!res.ok) {
    const text = await res.text();
    let msg = `API error ${res.status}`;
    try {
      const json = JSON.parse(text);
      msg = json.message || json.error || msg;
    } catch { msg = text || msg; }
    throw new Error(msg);
  }
  return res;
}

export async function executeTask(request: string, context?: object, agentName?: string): Promise<TaskExecutionResult> {
  const body: any = { request };
  if (context && Object.keys(context).length > 0) body.context = context;
  if (agentName) body.agentName = agentName;
  const res = await handleResponse(await fetch(`${BASE}/api/task/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }));
  return res.json();
}

export async function continueTask(request: string, conversationId: string, context?: object, agentName?: string): Promise<TaskExecutionResult> {
  const body: any = { request, conversationId };
  if (context && Object.keys(context).length > 0) body.context = context;
  if (agentName) body.agentName = agentName;
  const res = await handleResponse(await fetch(`${BASE}/api/task/continue`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }));
  return res.json();
}

export async function getSystemInfo(): Promise<SystemInfo> {
  const res = await handleResponse(await fetch(`${BASE}/api/task/info`));
  return res.json();
}

export async function getAgents(): Promise<string[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/agents`));
  const data = await res.json();
  return data.items || [];
}

export async function getModels(): Promise<ModelInfo[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/models`));
  const data = await res.json();
  return data.models || [];
}

export interface UsageStats {
  providers: Record<string, { promptTokens: number; completionTokens: number; totalTokens: number }>;
  totalTokens: number;
  ollamaAvailable: boolean;
  ollamaModels: { name: string; sizeBytes: number }[];
}

export async function getUsage(): Promise<UsageStats> {
  const res = await handleResponse(await fetch(`${BASE}/api/usage`));
  return res.json();
}

export async function getConversations(): Promise<ConversationSummary[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/conversations`));
  const data = await res.json();
  return data.conversations || [];
}

export async function getConversationMessages(id: string): Promise<{ role: string; content: string }[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/conversations/${encodeURIComponent(id)}/messages`));
  const data = await res.json();
  return data.messages || [];
}

export async function deleteConversation(id: string): Promise<void> {
  await handleResponse(await fetch(`${BASE}/api/conversations/${encodeURIComponent(id)}`, { method: 'DELETE' }));
}

export async function getTools(): Promise<string[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/tools`));
  const data = await res.json();
  return data.items || [];
}

export async function getSkills(): Promise<string[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/skills`));
  const data = await res.json();
  return data.items || [];
}

/**
 * Execute a task via SSE streaming endpoint.
 * Returns an EventSource-like reader that calls back on each event.
 */
export function streamTask(
  request: string,
  opts: {
    agentName?: string;
    conversationId?: string | null;
    context?: object;
    model?: string;
  },
  callbacks: {
    onStarted?: (data: any) => void;
    onResult?: (data: any) => void;
    onDone?: () => void;
    onError?: (error: string) => void;
  }
): AbortController {
  const controller = new AbortController();
  const body: any = { request };
  if (opts.agentName) body.agentName = opts.agentName;
  if (opts.conversationId) body.conversationId = opts.conversationId;
  if (opts.context && Object.keys(opts.context).length > 0) body.context = opts.context;
  if (opts.model) body.model = opts.model;

  fetch(`${BASE}/api/task/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const text = await res.text();
        callbacks.onError?.(text || `HTTP ${res.status}`);
        return;
      }
      const reader = res.body?.getReader();
      if (!reader) { callbacks.onError?.('No response body'); return; }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // Parse SSE events from buffer
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        let eventType = '';
        for (const line of lines) {
          if (line.startsWith('event: ')) {
            eventType = line.slice(7).trim();
          } else if (line.startsWith('data: ')) {
            const dataStr = line.slice(6);
            try {
              const data = JSON.parse(dataStr);
              switch (eventType) {
                case 'started': callbacks.onStarted?.(data); break;
                case 'result': callbacks.onResult?.(data); break;
                case 'done': callbacks.onDone?.(); break;
                case 'error': callbacks.onError?.(data.error || 'Unknown error'); break;
              }
            } catch {
              // Non-JSON data line, skip
            }
            eventType = '';
          }
        }
      }
      // If no done event was sent, trigger it
      callbacks.onDone?.();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError?.(err.message || 'Stream failed');
      }
    });

  return controller;
}
