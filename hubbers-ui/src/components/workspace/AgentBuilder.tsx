import { useState, useEffect, useMemo } from 'react';
import { Bot, Settings, Cpu, FileText, Wrench } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { SchemaEditor, schemaToYaml, yamlSchemaToModel } from '@/components/workspace/SchemaEditor';
import type { SchemaModel } from '@/components/workspace/SchemaEditor';
import type { RepoModel, ModelProviderInfo } from '@/types/workspace';
import { fetchModelProviders } from '@/lib/api';

interface AgentModel {
  name: string;
  version: string;
  description: string;
  mode: 'simple' | 'agentic';
  tools: string[];
  config: { max_iterations: number; timeout_seconds: number };
  model: { provider: string; name: string; temperature: number };
  inputSchema: SchemaModel;
  outputSchema: SchemaModel;
  systemPrompt: string;
}

function emptyAgent(): AgentModel {
  return {
    name: '', version: '1.0.0', description: '',
    mode: 'simple', tools: [],
    config: { max_iterations: 1, timeout_seconds: 300 },
    model: { provider: 'ollama', name: 'qwen3:4b', temperature: 0.0 },
    inputSchema: { type: 'object', properties: [{ name: 'request', type: 'string', description: 'Natural language request', required: true }] },
    outputSchema: { type: 'object', properties: [{ name: 'result', type: 'object', description: 'The result', required: true }] },
    systemPrompt: 'Answer the user\'s `{request}` clearly and concisely.\n\nReturn a JSON object with:\n- `result`: your answer',
  };
}

function parseAgentYaml(yaml: string): AgentModel | null {
  try {
    const lines = yaml.split('\n');
    const agent = emptyAgent();
    let section = '';
    let indent = 0;
    let subSection = '';
    let promptLines: string[] = [];
    let inPrompt = false;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;

      if (inPrompt) {
        promptLines.push(line.replace(/^ {4}/, ''));
        continue;
      }

      const topMatch = trimmed.match(/^(\w[\w.]*)\s*:\s*(.*)$/);
      if (topMatch && line.indexOf(topMatch[1]) === 0) {
        section = topMatch[1];
        const val = topMatch[2].trim();
        if (section === 'mode') agent.mode = val as 'simple' | 'agentic';
        if (section === 'tools' && val === '[]') agent.tools = [];
        subSection = '';
        continue;
      }

      if (section === 'agent') {
        const kv = trimmed.match(/^(\w+)\s*:\s*(.+)$/);
        if (kv) {
          if (kv[1] === 'name') agent.name = kv[2];
          if (kv[1] === 'version') agent.version = kv[2];
          if (kv[1] === 'description') agent.description = kv[2];
        }
      }

      if (section === 'tools' && trimmed.startsWith('- ')) {
        agent.tools.push(trimmed.replace(/^- /, ''));
      }

      if (section === 'config') {
        const kv = trimmed.match(/^(\w+)\s*:\s*(.+)$/);
        if (kv) {
          if (kv[1] === 'max_iterations') agent.config.max_iterations = parseInt(kv[2]) || 1;
          if (kv[1] === 'timeout_seconds') agent.config.timeout_seconds = parseInt(kv[2]) || 300;
        }
      }

      if (section === 'model') {
        const kv = trimmed.match(/^(\w+)\s*:\s*(.+)$/);
        if (kv) {
          if (kv[1] === 'provider') agent.model.provider = kv[2];
          if (kv[1] === 'name') agent.model.name = kv[2];
          if (kv[1] === 'temperature') agent.model.temperature = parseFloat(kv[2]) || 0;
        }
      }

      if (section === 'input' || section === 'output') {
        if (trimmed === 'properties:') subSection = 'properties';
      }

      if (section === 'instructions') {
        if (trimmed.startsWith('system_prompt: |')) {
          inPrompt = true;
          continue;
        }
      }
    }

    if (promptLines.length > 0) {
      agent.systemPrompt = promptLines.join('\n').trim();
    }

    // Parse input/output schemas from a simple approach - re-parse the YAML for properties
    const inputMatch = yaml.match(/^input:\s*\n([\s\S]*?)(?=^output:|^instructions:|^$)/m);
    if (inputMatch) {
      agent.inputSchema = parseSchemaBlock(inputMatch[1]);
    }
    const outputMatch = yaml.match(/^output:\s*\n([\s\S]*?)(?=^instructions:|^input:|^$)/m);
    if (outputMatch) {
      agent.outputSchema = parseSchemaBlock(outputMatch[1]);
    }

    return agent;
  } catch {
    return null;
  }
}

function parseSchemaBlock(block: string): SchemaModel {
  const fields: SchemaModel['properties'] = [];
  const lines = block.split('\n');
  let currentField: any = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    // Property name (indented under "properties:")
    const propMatch = trimmed.match(/^(\w[\w_]*)\s*:\s*$/);
    if (propMatch && line.match(/^ {4,6}\w/)) {
      if (currentField) fields.push(currentField);
      currentField = { name: propMatch[1], type: 'string', description: '', required: false };
      continue;
    }

    if (currentField) {
      const kv = trimmed.match(/^(\w+)\s*:\s*(.+)$/);
      if (kv) {
        if (kv[1] === 'type') currentField.type = kv[2];
        if (kv[1] === 'required') currentField.required = kv[2] === 'true';
        if (kv[1] === 'description') currentField.description = kv[2];
      }
    }
  }
  if (currentField) fields.push(currentField);
  return { type: 'object', properties: fields };
}

function agentToYaml(agent: AgentModel): string {
  const lines: string[] = [];
  lines.push('agent:');
  lines.push(`  name: ${agent.name}`);
  lines.push(`  version: ${agent.version}`);
  lines.push(`  description: ${agent.description}`);
  lines.push('');
  lines.push(`mode: ${agent.mode}`);
  if (agent.tools.length === 0) {
    lines.push('tools: []');
  } else {
    lines.push('tools:');
    agent.tools.forEach(t => lines.push(`  - ${t}`));
  }
  lines.push('config:');
  lines.push(`  max_iterations: ${agent.config.max_iterations}`);
  lines.push(`  timeout_seconds: ${agent.config.timeout_seconds}`);
  lines.push('');
  lines.push('model:');
  lines.push(`  provider: ${agent.model.provider}`);
  lines.push(`  name: ${agent.model.name}`);
  lines.push(`  temperature: ${agent.model.temperature}`);
  lines.push('');
  lines.push('input:');
  lines.push(schemaToYaml(agent.inputSchema, 2));
  lines.push('');
  lines.push('output:');
  lines.push(schemaToYaml(agent.outputSchema, 2));
  lines.push('');
  lines.push('instructions:');
  lines.push('  system_prompt: |');
  agent.systemPrompt.split('\n').forEach(l => lines.push(`    ${l}`));
  lines.push('');
  return lines.join('\n');
}

interface AgentBuilderProps {
  manifest: string;
  onManifestChange: (yaml: string) => void;
  repo: RepoModel;
}

export function AgentBuilder({ manifest, onManifestChange, repo }: AgentBuilderProps) {
  const [agent, setAgent] = useState<AgentModel>(() => parseAgentYaml(manifest) || emptyAgent());
  const [providers, setProviders] = useState<ModelProviderInfo[]>([]);

  useEffect(() => {
    fetchModelProviders().then(setProviders).catch(() => setProviders([]));
  }, []);

  // Sync back to YAML whenever agent model changes
  const yaml = useMemo(() => agentToYaml(agent), [agent]);

  const syncToYaml = () => onManifestChange(yaml);

  const update = (patch: Partial<AgentModel>) => setAgent(prev => ({ ...prev, ...patch }));
  const updateConfig = (patch: Partial<AgentModel['config']>) => setAgent(prev => ({ ...prev, config: { ...prev.config, ...patch } }));
  const updateModel = (patch: Partial<AgentModel['model']>) => setAgent(prev => ({ ...prev, model: { ...prev.model, ...patch } }));

  const toggleTool = (tool: string) => {
    setAgent(prev => ({
      ...prev,
      tools: prev.tools.includes(tool) ? prev.tools.filter(t => t !== tool) : [...prev.tools, tool],
    }));
  };

  const availableTools = repo.tools.map(t => t.name);

  return (
    <ScrollArea className="h-full">
      <div className="p-4 space-y-4">
        {/* Sync bar */}
        <div className="flex items-center justify-between">
          <p className="text-[10px] text-muted-foreground">Visual editor — changes sync to YAML on click</p>
          <Button size="sm" className="h-7 text-xs gap-1" onClick={syncToYaml}>
            Sync to YAML
          </Button>
        </div>

        {/* Metadata */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Bot className="w-3 h-3" /> Agent Metadata
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <div className="grid grid-cols-[1fr_100px] gap-2">
              <div>
                <Label className="text-[10px]">Name</Label>
                <Input value={agent.name} onChange={e => update({ name: e.target.value })} placeholder="my.agent" className="h-7 text-xs font-mono" />
              </div>
              <div>
                <Label className="text-[10px]">Version</Label>
                <Input value={agent.version} onChange={e => update({ version: e.target.value })} placeholder="1.0.0" className="h-7 text-xs" />
              </div>
            </div>
            <div>
              <Label className="text-[10px]">Description</Label>
              <Input value={agent.description} onChange={e => update({ description: e.target.value })} placeholder="What does this agent do?" className="h-7 text-xs" />
            </div>
          </CardContent>
        </Card>

        {/* Mode & Config */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Settings className="w-3 h-3" /> Mode & Configuration
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <div className="grid grid-cols-3 gap-2">
              <div>
                <Label className="text-[10px]">Mode</Label>
                <Select value={agent.mode} onValueChange={v => update({ mode: v as 'simple' | 'agentic' })}>
                  <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="simple">Simple (single LLM call)</SelectItem>
                    <SelectItem value="agentic">Agentic (ReAct loop)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="text-[10px]">Max Iterations</Label>
                <Input type="number" value={agent.config.max_iterations} onChange={e => updateConfig({ max_iterations: parseInt(e.target.value) || 1 })} className="h-7 text-xs" min={1} max={50} />
              </div>
              <div>
                <Label className="text-[10px]">Timeout (sec)</Label>
                <Input type="number" value={agent.config.timeout_seconds} onChange={e => updateConfig({ timeout_seconds: parseInt(e.target.value) || 300 })} className="h-7 text-xs" min={10} max={3600} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Model */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Cpu className="w-3 h-3" /> Model
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <div className="grid grid-cols-3 gap-2">
              <div>
                <Label className="text-[10px]">Provider</Label>
                <Select value={agent.model.provider} onValueChange={v => updateModel({ provider: v })}>
                  <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {(providers.length ? providers : [
                      { id: 'ollama', label: 'Ollama', local: true, configured: true },
                      { id: 'llama-cpp', label: 'llama.cpp', local: true, configured: false },
                      { id: 'openai', label: 'OpenAI', local: false, configured: false },
                      { id: 'anthropic', label: 'Anthropic', local: false, configured: false },
                    ]).map(provider => (
                      <SelectItem key={provider.id} value={provider.id}>
                        {provider.label}{provider.local ? ' (Local)' : ''}{provider.configured ? '' : ' - not configured'}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="text-[10px]">Model Name</Label>
                <Input value={agent.model.name} onChange={e => updateModel({ name: e.target.value })} placeholder="qwen3:4b" className="h-7 text-xs font-mono" />
              </div>
              <div>
                <Label className="text-[10px]">Temperature</Label>
                <Input type="number" value={agent.model.temperature} onChange={e => updateModel({ temperature: parseFloat(e.target.value) || 0 })} className="h-7 text-xs" min={0} max={2} step={0.1} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Tools */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Wrench className="w-3 h-3" /> Tools
              {agent.tools.length > 0 && <Badge variant="secondary" className="text-[9px] ml-1">{agent.tools.length}</Badge>}
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3">
            {availableTools.length === 0 ? (
              <p className="text-[10px] text-muted-foreground text-center py-2">No tools in repository</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {availableTools.map(tool => (
                  <Badge
                    key={tool}
                    variant={agent.tools.includes(tool) ? 'default' : 'outline'}
                    className="text-[10px] cursor-pointer select-none transition-colors"
                    onClick={() => toggleTool(tool)}
                  >
                    {tool}
                  </Badge>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Input Schema */}
        <SchemaEditor
          label="Input Schema"
          schema={agent.inputSchema}
          onChange={s => update({ inputSchema: s })}
        />

        {/* Output Schema */}
        <SchemaEditor
          label="Output Schema"
          schema={agent.outputSchema}
          onChange={s => update({ outputSchema: s })}
        />

        {/* System Prompt */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <FileText className="w-3 h-3" /> System Prompt
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3">
            <p className="text-[9px] text-muted-foreground mb-1.5">
              Use {'`{field_name}`'} to reference input fields. Available: {agent.inputSchema.properties.map(f => `{${f.name}}`).join(', ') || 'none'}
            </p>
            <Textarea
              value={agent.systemPrompt}
              onChange={e => update({ systemPrompt: e.target.value })}
              placeholder="Enter system prompt..."
              className="min-h-[200px] font-mono text-xs resize-y"
            />
          </CardContent>
        </Card>
      </div>
    </ScrollArea>
  );
}
