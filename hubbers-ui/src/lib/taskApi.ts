import type { TaskExecutionResult, SystemInfo } from '@/types/chat';

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

export async function executeTask(request: string, context?: object): Promise<TaskExecutionResult> {
  const body: any = { request };
  if (context && Object.keys(context).length > 0) body.context = context;
  const res = await handleResponse(await fetch(`${BASE}/api/task/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }));
  return res.json();
}

export async function continueTask(request: string, conversationId: string, context?: object): Promise<TaskExecutionResult> {
  const body: any = { request, conversationId };
  if (context && Object.keys(context).length > 0) body.context = context;
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
