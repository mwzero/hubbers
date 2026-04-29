import { useState } from 'react';
import { Plus, Trash2, GripVertical } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

export interface SchemaField {
  name: string;
  type: 'string' | 'integer' | 'number' | 'boolean' | 'object' | 'array';
  description: string;
  required: boolean;
}

export interface SchemaModel {
  type: 'object';
  properties: SchemaField[];
}

interface SchemaEditorProps {
  schema: SchemaModel;
  onChange: (schema: SchemaModel) => void;
  label?: string;
}

const FIELD_TYPES = ['string', 'integer', 'number', 'boolean', 'object', 'array'] as const;

export function schemaToYaml(schema: SchemaModel, indent: number = 2): string {
  const pad = ' '.repeat(indent);
  const lines: string[] = [`${pad}type: object`, `${pad}properties:`];
  for (const field of schema.properties) {
    lines.push(`${pad}  ${field.name}:`);
    lines.push(`${pad}    type: ${field.type}`);
    if (field.required) lines.push(`${pad}    required: true`);
    if (field.description) lines.push(`${pad}    description: ${field.description}`);
  }
  return lines.join('\n');
}

export function yamlSchemaToModel(obj: any): SchemaModel {
  const properties: SchemaField[] = [];
  const requiredList: string[] = obj?.required || [];
  const props = obj?.properties || obj?.schema?.properties || {};
  for (const [name, def] of Object.entries(props)) {
    const d = def as any;
    properties.push({
      name,
      type: d.type || 'string',
      description: d.description || '',
      required: d.required === true || requiredList.includes(name),
    });
  }
  return { type: 'object', properties };
}

export function SchemaEditor({ schema, onChange, label }: SchemaEditorProps) {
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  const addField = () => {
    onChange({
      ...schema,
      properties: [...schema.properties, { name: '', type: 'string', description: '', required: false }],
    });
  };

  const removeField = (idx: number) => {
    onChange({ ...schema, properties: schema.properties.filter((_, i) => i !== idx) });
  };

  const updateField = (idx: number, patch: Partial<SchemaField>) => {
    const updated = [...schema.properties];
    updated[idx] = { ...updated[idx], ...patch };
    onChange({ ...schema, properties: updated });
  };

  const handleDragStart = (idx: number) => setDragIdx(idx);
  const handleDragOver = (e: React.DragEvent, idx: number) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const items = [...schema.properties];
    const [moved] = items.splice(dragIdx, 1);
    items.splice(idx, 0, moved);
    onChange({ ...schema, properties: items });
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  return (
    <Card>
      <CardHeader className="py-2 px-3 flex flex-row items-center justify-between">
        <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
          {label || 'Schema'}
        </CardTitle>
        <Button variant="outline" size="sm" className="h-6 text-[10px] gap-1" onClick={addField}>
          <Plus className="w-3 h-3" /> Field
        </Button>
      </CardHeader>
      <CardContent className="px-3 pb-3 space-y-1.5">
        {schema.properties.length === 0 && (
          <p className="text-[10px] text-muted-foreground text-center py-3">No fields. Click "+ Field" to add.</p>
        )}
        {schema.properties.map((field, idx) => (
          <div
            key={idx}
            draggable
            onDragStart={() => handleDragStart(idx)}
            onDragOver={e => handleDragOver(e, idx)}
            onDragEnd={handleDragEnd}
            className={`grid grid-cols-[16px_1fr_100px_1fr_40px_28px] gap-1.5 items-center group ${dragIdx === idx ? 'opacity-50' : ''}`}
          >
            <GripVertical className="w-3 h-3 text-muted-foreground cursor-grab opacity-0 group-hover:opacity-100 transition-opacity" />
            <Input
              value={field.name}
              onChange={e => updateField(idx, { name: e.target.value })}
              placeholder="name"
              className="h-7 text-xs font-mono"
            />
            <Select value={field.type} onValueChange={v => updateField(idx, { type: v as SchemaField['type'] })}>
              <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                {FIELD_TYPES.map(t => <SelectItem key={t} value={t}>{t}</SelectItem>)}
              </SelectContent>
            </Select>
            <Input
              value={field.description}
              onChange={e => updateField(idx, { description: e.target.value })}
              placeholder="description"
              className="h-7 text-xs"
            />
            <div className="flex items-center justify-center">
              <Switch
                checked={field.required}
                onCheckedChange={v => updateField(idx, { required: v })}
                className="scale-75"
              />
            </div>
            <Button variant="ghost" size="icon" className="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity" onClick={() => removeField(idx)}>
              <Trash2 className="w-3 h-3 text-destructive" />
            </Button>
          </div>
        ))}
        {schema.properties.length > 0 && (
          <div className="grid grid-cols-[16px_1fr_100px_1fr_40px_28px] gap-1.5 text-[9px] text-muted-foreground px-0.5">
            <span />
            <span>Name</span>
            <span>Type</span>
            <span>Description</span>
            <span className="text-center">Req</span>
            <span />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
