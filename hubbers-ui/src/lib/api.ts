import type { ArtifactType, ValidationResult, Execution, Step, FormDef, ToolDriverInfo, ModelProviderInfo, ArtifactStatus } from '@/types/workspace';
import type { AppConfig } from '@/types/settings';

const BASE = '';

async function handleResponse(res: Response) {
  if (!res.ok) {
    const text = await res.text();
    let msg = `API error ${res.status}`;
    try {
      const json = JSON.parse(text);
      msg = json.message || json.error || json.errors?.join(', ') || msg;
    } catch { msg = text || msg; }
    throw new Error(msg);
  }
  return res;
}

export async function checkHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/api/health`);
    return res.ok;
  } catch {
    return false;
  }
}

export async function fetchArtifacts(type: 'agents' | 'tools' | 'pipelines' | 'skills'): Promise<string[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/${type}`));
  const data = await res.json();
  return data.items || [];
}

export async function fetchToolDrivers(): Promise<ToolDriverInfo[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/catalog/drivers`));
  const data = await res.json();
  return data.drivers || [];
}

export async function fetchModelProviders(): Promise<ModelProviderInfo[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/catalog/model-providers`));
  const data = await res.json();
  return data.providers || [];
}

export async function fetchArtifactStatus(type: ArtifactType, name: string): Promise<ArtifactStatus> {
  const plural = type === 'skill' ? 'skills' : `${type}s`;
  const res = await handleResponse(await fetch(`${BASE}/api/artifacts/${plural}/${name}/status`));
  return res.json();
}

export interface GatewayStatus {
  mcp: { configured: boolean; streamableHttp: boolean; sse: boolean; endpoint: string; sseEndpoint: string };
  openAiCompatible: { configured: boolean; modelsEndpoint: string; chatCompletionsEndpoint: string; toolsEndpoint: string };
  policy: { apiKeyRequired: boolean; certifiedOnly: boolean; exposedArtifacts: Record<string, number> };
}

export async function fetchGatewayStatus(): Promise<GatewayStatus> {
  const res = await handleResponse(await fetch(`${BASE}/api/gateway/status`));
  return res.json();
}

export async function fetchManifest(type: ArtifactType, name: string): Promise<string> {
  const plural = type === 'skill' ? 'skills' : `${type}s`;
  const res = await handleResponse(await fetch(`${BASE}/api/manifest/${plural}/${name}`));
  return res.text();
}

export async function saveManifest(type: ArtifactType, name: string, yaml: string): Promise<void> {
  const plural = type === 'skill' ? 'skills' : `${type}s`;
  const contentType = type === 'skill' ? 'text/plain' : 'text/yaml';
  await handleResponse(await fetch(`${BASE}/api/manifest/${plural}/${name}`, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: yaml,
  }));
}

export async function validateManifest(type: ArtifactType, yaml: string): Promise<ValidationResult> {
  const plural = type === 'skill' ? 'skills' : `${type}s`;
  const contentType = type === 'skill' ? 'text/plain' : 'text/yaml';
  const res = await handleResponse(await fetch(`${BASE}/api/validate/${plural}`, {
    method: 'POST',
    headers: { 'Content-Type': contentType },
    body: yaml,
  }));
  return res.json();
}

export async function fetchToolForm(name: string): Promise<FormDef | null> {
  try {
    const res = await fetch(`${BASE}/api/run/tools/${name}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    });
    if (!res.ok) return null;
    const data = await res.json();
    return data.requiresForm && data.form ? data.form : null;
  } catch {
    return null;
  }
}

export async function runArtifact(type: ArtifactType, name: string, input: any): Promise<{ data?: any; result?: any; tools_used?: string[]; reasoning?: string; requiresForm?: boolean; formSessionId?: string; form?: FormDef }> {
  const plural = type === 'skill' ? 'skills' : `${type}s`;
  const res = await handleResponse(await fetch(`${BASE}/api/run/${plural}/${name}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }));
  return res.json();
}

export async function submitForm(sessionId: string, data: Record<string, any>): Promise<any> {
  const res = await handleResponse(await fetch(`${BASE}/api/forms/${sessionId}/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }));
  return res.json();
}

export async function fetchExecutions(): Promise<Execution[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions`));
  const data = await res.json();
  return data.items || [];
}

export async function fetchExecutionDetail(id: string): Promise<Execution> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${id}`));
  return res.json();
}

export async function fetchExecutionLog(id: string): Promise<string> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${id}/log`));
  return res.text();
}

export async function fetchExecutionInput(id: string): Promise<any> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${id}/input`));
  return res.json();
}

export async function fetchExecutionOutput(id: string): Promise<any> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${id}/output`));
  return res.json();
}

export async function fetchExecutionSteps(id: string): Promise<Step[]> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${id}/steps`));
  const data = await res.json();
  return data.items || [];
}

export async function fetchStepInput(executionId: string, stepName: string): Promise<any> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${executionId}/steps/${stepName}/input`));
  return res.json();
}

export async function fetchStepOutput(executionId: string, stepName: string): Promise<any> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${executionId}/steps/${stepName}/output`));
  return res.json();
}

export async function fetchStepLog(executionId: string, stepName: string): Promise<string> {
  const res = await handleResponse(await fetch(`${BASE}/api/executions/${executionId}/steps/${stepName}/log`));
  return res.text();
}

export async function fetchSettings(): Promise<AppConfig> {
  const res = await handleResponse(await fetch(`${BASE}/api/settings`));
  return res.json();
}

export async function saveSettings(config: AppConfig): Promise<void> {
  await handleResponse(await fetch(`${BASE}/api/settings`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  }));
}
