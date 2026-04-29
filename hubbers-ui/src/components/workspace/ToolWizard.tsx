import { useEffect, useState, useMemo } from 'react';
import { Wrench, Settings, FileInput, FileOutput, FlaskConical, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Switch } from '@/components/ui/switch';
import { SchemaEditor, schemaToYaml, yamlSchemaToModel } from '@/components/workspace/SchemaEditor';
import type { SchemaModel, SchemaField } from '@/components/workspace/SchemaEditor';
import { fetchToolDrivers } from '@/lib/api';
import type { ToolDriverInfo } from '@/types/workspace';

const FALLBACK_DRIVER_TYPES: ToolDriverInfo[] = [
  { type: 'http', label: 'HTTP Request', description: 'HTTP API calls using manifest config', risk: 'network' },
  { type: 'csv.read', label: 'CSV Read', description: 'Read rows from a CSV file', risk: 'filesystem' },
  { type: 'csv.write', label: 'CSV Write', description: 'Write rows to a CSV file', risk: 'filesystem' },
  { type: 'file.ops', label: 'File Operations', description: 'Read, write, list, copy, move, or delete files', risk: 'high-risk' },
  { type: 'shell.exec', label: 'Shell Execute', description: 'Execute shell commands', risk: 'high-risk' },
];

interface FormFieldModel {
  name: string;
  type: 'text' | 'textarea' | 'number' | 'slider' | 'checkbox' | 'select';
  label: string;
  placeholder: string;
  required: boolean;
  defaultValue: string;
}

interface ExampleModel {
  name: string;
  description: string;
  inputJson: string;
  outputJson: string;
}

interface ToolModel {
  name: string;
  version: string;
  description: string;
  type: string;
  config: Record<string, string>;
  hasForms: boolean;
  formFields: FormFieldModel[];
  inputSchema: SchemaModel;
  outputSchema: SchemaModel;
  examples: ExampleModel[];
}

function emptyTool(): ToolModel {
  return {
    name: '', version: '1.0.0', description: '',
    type: 'http',
    config: {},
    hasForms: false, formFields: [],
    inputSchema: { type: 'object', properties: [] },
    outputSchema: { type: 'object', properties: [] },
    examples: [],
  };
}

function parseToolYaml(yaml: string): ToolModel | null {
  try {
    const tool = emptyTool();
    const lines = yaml.split('\n');
    let section = '';
    let subSection = '';

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;

      const topMatch = trimmed.match(/^(\w[\w.]*)\s*:\s*(.*)$/);
      if (topMatch && line.indexOf(topMatch[1]) === 0) {
        section = topMatch[1];
        subSection = '';
        if (section === 'type') tool.type = topMatch[2].trim();
        continue;
      }

      if (section === 'tool') {
        const kv = trimmed.match(/^(\w+)\s*:\s*(.+)$/);
        if (kv) {
          if (kv[1] === 'name') tool.name = kv[2];
          if (kv[1] === 'version') tool.version = kv[2];
          if (kv[1] === 'description') tool.description = kv[2].replace(/^>-?\s*/, '');
        }
      }

      if (section === 'config') {
        const kv = trimmed.match(/^(\w[\w_]*)\s*:\s*(.+)$/);
        if (kv) tool.config[kv[1]] = kv[2];
      }
    }

    // Parse input/output schemas
    const inputMatch = yaml.match(/^input:\s*\n\s*schema:\s*\n([\s\S]*?)(?=^output:|^forms:|^examples:|^config:|^$)/m);
    if (inputMatch) tool.inputSchema = parseBlock(inputMatch[1]);
    const outputMatch = yaml.match(/^output:\s*\n\s*schema:\s*\n([\s\S]*?)(?=^input:|^forms:|^examples:|^config:|^$)/m);
    if (outputMatch) tool.outputSchema = parseBlock(outputMatch[1]);

    return tool;
  } catch {
    return null;
  }
}

function parseBlock(block: string): SchemaModel {
  const fields: SchemaField[] = [];
  const lines = block.split('\n');
  let current: SchemaField | null = null;
  let inProperties = false;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === 'properties:') { inProperties = true; continue; }
    if (!inProperties || !trimmed) continue;

    const propMatch = trimmed.match(/^(\w[\w_]*)\s*:\s*$/);
    if (propMatch && line.match(/^ {6,8}\w/)) {
      if (current) fields.push(current);
      current = { name: propMatch[1], type: 'string', description: '', required: false };
      continue;
    }

    if (current) {
      const kv = trimmed.match(/^(\w+)\s*:\s*(.+)$/);
      if (kv) {
        if (kv[1] === 'type') current.type = kv[2] as SchemaField['type'];
        if (kv[1] === 'required') current.required = kv[2] === 'true';
        if (kv[1] === 'description') current.description = kv[2];
      }
    }
  }
  if (current) fields.push(current);
  return { type: 'object', properties: fields };
}

function toolToYaml(tool: ToolModel): string {
  const lines: string[] = [];
  lines.push('tool:');
  lines.push(`  name: ${tool.name}`);
  lines.push(`  version: ${tool.version}`);
  lines.push(`  description: ${tool.description}`);
  lines.push('');
  lines.push(`type: ${tool.type}`);

  if (tool.hasForms && tool.formFields.length > 0) {
    lines.push('');
    lines.push('forms:');
    lines.push('  before:');
    lines.push(`    title: "${tool.name} Configuration"`);
    lines.push('    fields:');
    for (const f of tool.formFields) {
      lines.push(`      - name: ${f.name}`);
      lines.push(`        type: ${f.type}`);
      if (f.label) lines.push(`        label: "${f.label}"`);
      if (f.placeholder) lines.push(`        placeholder: "${f.placeholder}"`);
      if (f.required) lines.push(`        required: true`);
      if (f.defaultValue) lines.push(`        defaultValue: "${f.defaultValue}"`);
    }
  }

  if (Object.keys(tool.config).length > 0) {
    lines.push('');
    lines.push('config:');
    for (const [k, v] of Object.entries(tool.config)) {
      lines.push(`  ${k}: ${v}`);
    }
  }

  lines.push('');
  lines.push('input:');
  lines.push('  schema:');
  lines.push(schemaToYaml(tool.inputSchema, 4));

  lines.push('');
  lines.push('output:');
  lines.push('  schema:');
  lines.push(schemaToYaml(tool.outputSchema, 4));

  if (tool.examples.length > 0) {
    lines.push('');
    lines.push('examples:');
    for (const ex of tool.examples) {
      lines.push(`  - name: ${ex.name}`);
      lines.push(`    description: ${ex.description}`);
      lines.push(`    input: ${ex.inputJson}`);
      lines.push(`    output: ${ex.outputJson}`);
    }
  }

  lines.push('');
  return lines.join('\n');
}

interface ToolWizardProps {
  manifest: string;
  onManifestChange: (yaml: string) => void;
}

export function ToolWizard({ manifest, onManifestChange }: ToolWizardProps) {
  const [tool, setTool] = useState<ToolModel>(() => parseToolYaml(manifest) || emptyTool());
  const [drivers, setDrivers] = useState<ToolDriverInfo[]>([]);
  const [configKey, setConfigKey] = useState('');
  const [configVal, setConfigVal] = useState('');

  useEffect(() => {
    fetchToolDrivers().then(setDrivers).catch(() => setDrivers([]));
  }, []);

  const yaml = useMemo(() => toolToYaml(tool), [tool]);
  const syncToYaml = () => onManifestChange(yaml);

  const update = (patch: Partial<ToolModel>) => setTool(prev => ({ ...prev, ...patch }));

  const addConfigEntry = () => {
    if (configKey.trim()) {
      update({ config: { ...tool.config, [configKey.trim()]: configVal } });
      setConfigKey('');
      setConfigVal('');
    }
  };

  const removeConfigEntry = (key: string) => {
    const next = { ...tool.config };
    delete next[key];
    update({ config: next });
  };

  const addFormField = () => {
    update({
      formFields: [...tool.formFields, { name: '', type: 'text', label: '', placeholder: '', required: false, defaultValue: '' }],
    });
  };

  const updateFormField = (idx: number, patch: Partial<FormFieldModel>) => {
    const updated = [...tool.formFields];
    updated[idx] = { ...updated[idx], ...patch };
    update({ formFields: updated });
  };

  const removeFormField = (idx: number) => {
    update({ formFields: tool.formFields.filter((_, i) => i !== idx) });
  };

  const addExample = () => {
    update({
      examples: [...tool.examples, { name: '', description: '', inputJson: '{}', outputJson: '{}' }],
    });
  };

  const updateExample = (idx: number, patch: Partial<ExampleModel>) => {
    const updated = [...tool.examples];
    updated[idx] = { ...updated[idx], ...patch };
    update({ examples: updated });
  };

  const removeExample = (idx: number) => {
    update({ examples: tool.examples.filter((_, i) => i !== idx) });
  };

  return (
    <ScrollArea className="h-full">
      <div className="p-4 space-y-4">
        {/* Sync bar */}
        <div className="flex items-center justify-between">
          <p className="text-[10px] text-muted-foreground">Tool configuration wizard — sync changes to YAML</p>
          <Button size="sm" className="h-7 text-xs gap-1" onClick={syncToYaml}>
            Sync to YAML
          </Button>
        </div>

        {/* Metadata */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Wrench className="w-3 h-3" /> Tool Metadata
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <div className="grid grid-cols-[1fr_100px] gap-2">
              <div>
                <Label className="text-[10px]">Name</Label>
                <Input value={tool.name} onChange={e => update({ name: e.target.value })} placeholder="my.tool" className="h-7 text-xs font-mono" />
              </div>
              <div>
                <Label className="text-[10px]">Version</Label>
                <Input value={tool.version} onChange={e => update({ version: e.target.value })} placeholder="1.0.0" className="h-7 text-xs" />
              </div>
            </div>
            <div>
              <Label className="text-[10px]">Description</Label>
              <Textarea value={tool.description} onChange={e => update({ description: e.target.value })} placeholder="What does this tool do?" className="min-h-[50px] text-xs resize-y" />
            </div>
          </CardContent>
        </Card>

        {/* Driver Type */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Settings className="w-3 h-3" /> Driver Type
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <Select value={tool.type} onValueChange={v => update({ type: v })}>
              <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                {(drivers.length ? drivers : FALLBACK_DRIVER_TYPES).map(d => (
                  <SelectItem key={d.type} value={d.type}>{d.label} <span className="text-muted-foreground ml-1">({d.type})</span></SelectItem>
                ))}
              </SelectContent>
            </Select>
            {(drivers.length ? drivers : FALLBACK_DRIVER_TYPES).find(d => d.type === tool.type) && (
              <p className="text-[10px] text-muted-foreground">
                {(drivers.length ? drivers : FALLBACK_DRIVER_TYPES).find(d => d.type === tool.type)?.description}
                <Badge variant={(drivers.length ? drivers : FALLBACK_DRIVER_TYPES).find(d => d.type === tool.type)?.risk === 'high-risk' ? 'destructive' : 'secondary'} className="ml-2 text-[9px]">
                  {(drivers.length ? drivers : FALLBACK_DRIVER_TYPES).find(d => d.type === tool.type)?.risk}
                </Badge>
              </p>
            )}
          </CardContent>
        </Card>

        {/* Config */}
        <Card>
          <CardHeader className="py-2 px-3 flex flex-row items-center justify-between">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">Config</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-1.5">
            {Object.entries(tool.config).map(([k, v]) => (
              <div key={k} className="flex gap-1 items-center group">
                <Badge variant="outline" className="text-[10px] font-mono">{k}</Badge>
                <span className="text-xs text-muted-foreground flex-1 truncate">{v}</span>
                <Button variant="ghost" size="icon" className="h-5 w-5 opacity-0 group-hover:opacity-100" onClick={() => removeConfigEntry(k)}>
                  <Trash2 className="w-3 h-3 text-destructive" />
                </Button>
              </div>
            ))}
            <div className="flex gap-1">
              <Input value={configKey} onChange={e => setConfigKey(e.target.value)} placeholder="key" className="h-7 text-xs font-mono w-1/3" />
              <Input value={configVal} onChange={e => setConfigVal(e.target.value)} placeholder="value" className="h-7 text-xs flex-1" />
              <Button variant="outline" size="sm" className="h-7 text-xs" onClick={addConfigEntry}>Add</Button>
            </div>
          </CardContent>
        </Card>

        {/* Forms */}
        <Card>
          <CardHeader className="py-2 px-3 flex flex-row items-center justify-between">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              User Input Form
              <Switch
                checked={tool.hasForms}
                onCheckedChange={v => update({ hasForms: v })}
                className="scale-75 ml-2"
              />
            </CardTitle>
            {tool.hasForms && (
              <Button variant="outline" size="sm" className="h-6 text-[10px] gap-1" onClick={addFormField}>
                <Plus className="w-3 h-3" /> Field
              </Button>
            )}
          </CardHeader>
          {tool.hasForms && (
            <CardContent className="px-3 pb-3 space-y-2">
              {tool.formFields.map((f, idx) => (
                <div key={idx} className="grid grid-cols-[1fr_80px_1fr_1fr_40px_28px] gap-1 items-center group">
                  <Input value={f.name} onChange={e => updateFormField(idx, { name: e.target.value })} placeholder="name" className="h-7 text-xs font-mono" />
                  <Select value={f.type} onValueChange={v => updateFormField(idx, { type: v as FormFieldModel['type'] })}>
                    <SelectTrigger className="h-7 text-[10px]"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {(['text', 'textarea', 'number', 'slider', 'checkbox', 'select'] as const).map(t => (
                        <SelectItem key={t} value={t}>{t}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input value={f.label} onChange={e => updateFormField(idx, { label: e.target.value })} placeholder="label" className="h-7 text-xs" />
                  <Input value={f.defaultValue} onChange={e => updateFormField(idx, { defaultValue: e.target.value })} placeholder="default" className="h-7 text-xs" />
                  <div className="flex items-center justify-center">
                    <Switch checked={f.required} onCheckedChange={v => updateFormField(idx, { required: v })} className="scale-75" />
                  </div>
                  <Button variant="ghost" size="icon" className="h-6 w-6 opacity-0 group-hover:opacity-100" onClick={() => removeFormField(idx)}>
                    <Trash2 className="w-3 h-3 text-destructive" />
                  </Button>
                </div>
              ))}
              {tool.formFields.length > 0 && (
                <div className="grid grid-cols-[1fr_80px_1fr_1fr_40px_28px] gap-1 text-[9px] text-muted-foreground">
                  <span>Name</span><span>Type</span><span>Label</span><span>Default</span><span className="text-center">Req</span><span />
                </div>
              )}
            </CardContent>
          )}
        </Card>

        {/* Input Schema */}
        <SchemaEditor label="Input Schema" schema={tool.inputSchema} onChange={s => update({ inputSchema: s })} />

        {/* Output Schema */}
        <SchemaEditor label="Output Schema" schema={tool.outputSchema} onChange={s => update({ outputSchema: s })} />

        {/* Examples */}
        <Card>
          <CardHeader className="py-2 px-3 flex flex-row items-center justify-between">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <FlaskConical className="w-3 h-3" /> Examples
            </CardTitle>
            <Button variant="outline" size="sm" className="h-6 text-[10px] gap-1" onClick={addExample}>
              <Plus className="w-3 h-3" /> Example
            </Button>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            {tool.examples.length === 0 && (
              <p className="text-[10px] text-muted-foreground text-center py-2">No examples. Add one for documentation and testing.</p>
            )}
            {tool.examples.map((ex, idx) => (
              <Card key={idx} className="border-l-[3px] border-l-blue-500">
                <CardContent className="p-2 space-y-1.5">
                  <div className="flex gap-1.5 items-center">
                    <Input value={ex.name} onChange={e => updateExample(idx, { name: e.target.value })} placeholder="example-name" className="h-7 text-xs font-mono flex-1" />
                    <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => removeExample(idx)}>
                      <Trash2 className="w-3 h-3 text-destructive" />
                    </Button>
                  </div>
                  <Input value={ex.description} onChange={e => updateExample(idx, { description: e.target.value })} placeholder="Description" className="h-7 text-xs" />
                  <div className="grid grid-cols-2 gap-1.5">
                    <div>
                      <Label className="text-[9px]">Input JSON</Label>
                      <Textarea value={ex.inputJson} onChange={e => updateExample(idx, { inputJson: e.target.value })} className="min-h-[60px] text-[10px] font-mono resize-y" />
                    </div>
                    <div>
                      <Label className="text-[9px]">Output JSON</Label>
                      <Textarea value={ex.outputJson} onChange={e => updateExample(idx, { outputJson: e.target.value })} className="min-h-[60px] text-[10px] font-mono resize-y" />
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </CardContent>
        </Card>
      </div>
    </ScrollArea>
  );
}
