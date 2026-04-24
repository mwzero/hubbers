import { useState, useEffect, useRef } from 'react';
import { Plus, Trash2, RefreshCw, Link2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { ScrollArea } from '@/components/ui/scroll-area';
import type { ArtifactType, StepInputMapping, FormDef } from '@/types/workspace';
import * as api from '@/lib/api';

// ---------------------------------------------------------------------------
// Reference picker popover â€” insert ${steps.id.output.field} into a value
// ---------------------------------------------------------------------------

interface RefPickerProps {
  prevStepIds: string[];
  onInsert: (ref: string) => void;
}

function RefPicker({ prevStepIds, onInsert }: RefPickerProps) {
  const [open, setOpen] = useState(false);

  const suggestions = prevStepIds.flatMap(id => [
    { label: `${id} â€” full output`, value: `\${steps.${id}.output}` },
    { label: `${id} â€” fieldâ€¦`, value: `\${steps.${id}.output.}` },
  ]);

  if (prevStepIds.length === 0) return null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 shrink-0 text-muted-foreground hover:text-primary"
          title="Insert step reference"
        >
          <Link2 className="w-3 h-3" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-64 p-2">
        <p className="text-[10px] font-semibold text-muted-foreground uppercase mb-1.5">Insert step reference</p>
        <ScrollArea className="max-h-48">
          {suggestions.map(s => (
            <button
              key={s.value}
              className="w-full text-left px-2 py-1 rounded text-xs font-mono hover:bg-accent transition-colors"
              onClick={() => { onInsert(s.value); setOpen(false); }}
            >
              <span className="text-primary">{s.value}</span>
              <span className="ml-2 text-[10px] text-muted-foreground">{s.label}</span>
            </button>
          ))}
        </ScrollArea>
      </PopoverContent>
    </Popover>
  );
}

// ---------------------------------------------------------------------------
// Expression-aware text input (single line) â€” shows native input + ref picker
// ---------------------------------------------------------------------------

interface ExprInputProps {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  prevStepIds: string[];
}

function ExprInput({ value, onChange, placeholder, prevStepIds }: ExprInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  const insertRef = (ref: string) => {
    const el = inputRef.current;
    if (!el) { onChange(value + ref); return; }
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? value.length;
    const next = value.slice(0, start) + ref + value.slice(end);
    onChange(next);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + ref.length, start + ref.length);
    });
  };

  return (
    <div className="flex gap-1 flex-1">
      <Input
        ref={inputRef}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-7 text-xs font-mono flex-1"
      />
      <RefPicker prevStepIds={prevStepIds} onInsert={insertRef} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Expression-aware textarea â€” shows textarea + ref picker button above it
// ---------------------------------------------------------------------------

interface ExprTextareaProps {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  prevStepIds: string[];
}

function ExprTextarea({ value, onChange, placeholder, prevStepIds }: ExprTextareaProps) {
  const taRef = useRef<HTMLTextAreaElement>(null);

  const insertRef = (ref: string) => {
    const el = taRef.current;
    if (!el) { onChange(value + ref); return; }
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? value.length;
    const next = value.slice(0, start) + ref + value.slice(end);
    onChange(next);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + ref.length, start + ref.length);
    });
  };

  return (
    <div className="space-y-1 flex-1">
      <div className="flex justify-end">
        <RefPicker prevStepIds={prevStepIds} onInsert={insertRef} />
      </div>
      <Textarea
        ref={taRef}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        className="text-xs font-mono min-h-[60px]"
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

export interface StepInputPanelProps {
  targetType: ArtifactType;
  targetName: string;
  inputMapping: StepInputMapping[];
  onInputMappingChange: (mapping: StepInputMapping[]) => void;
  prevStepIds: string[];
}

export function StepInputPanel({
  targetType,
  targetName,
  inputMapping,
  onInputMappingChange,
  prevStepIds,
}: StepInputPanelProps) {
  const [formDef, setFormDef] = useState<FormDef | null>(null);
  const [loadingForm, setLoadingForm] = useState(false);

  // Helper: get / set a single mapping entry by key
  const getValue = (key: string) => inputMapping.find(m => m.key === key)?.expression ?? '';
  const setValue = (key: string, expression: string) => {
    const exists = inputMapping.some(m => m.key === key);
    if (exists) {
      onInputMappingChange(inputMapping.map(m => m.key === key ? { key, expression } : m));
    } else {
      onInputMappingChange([...inputMapping, { key, expression }]);
    }
  };

  // Load form definition for tools
  useEffect(() => {
    if (targetType !== 'tool' || !targetName) { setFormDef(null); return; }
    let cancelled = false;
    setLoadingForm(true);
    api.fetchToolForm(targetName).then(def => {
      if (cancelled) return;
      setFormDef(def);
      // Pre-populate mapping keys from form fields if mapping is empty
      if (def && inputMapping.length === 0) {
        onInputMappingChange(def.fields.map(f => ({ key: f.name, expression: '' })));
      }
      setLoadingForm(false);
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetType, targetName]);

  // Manual key/expression table row helpers
  const updateRow = (idx: number, field: 'key' | 'expression', val: string) => {
    const next = [...inputMapping];
    next[idx] = { ...next[idx], [field]: val };
    onInputMappingChange(next);
  };
  const addRow = () => onInputMappingChange([...inputMapping, { key: '', expression: '' }]);
  const removeRow = (idx: number) => onInputMappingChange(inputMapping.filter((_, i) => i !== idx));

  if (loadingForm) {
    return (
      <div className="flex items-center gap-1.5 py-1 text-[10px] text-muted-foreground">
        <RefreshCw className="w-3 h-3 animate-spin" /> Loading formâ€¦
      </div>
    );
  }

  // â”€â”€ Form-based mode (tool with form definition) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  if (formDef) {
    return (
      <div className="space-y-3 pt-1">
        {formDef.description && (
          <p className="text-[10px] text-muted-foreground">{formDef.description}</p>
        )}
        {formDef.fields.map(field => {
          const val = getValue(field.name);
          const isRef = val.includes('${');
          return (
            <div key={field.name} className="space-y-1">
              <Label className="text-[10px]">
                {field.label || field.name}
                {field.required && <span className="text-destructive ml-0.5">*</span>}
              </Label>

              {/* text â†’ ExprInput */}
              {field.type === 'text' && (
                <ExprInput
                  value={val}
                  onChange={v => setValue(field.name, v)}
                  placeholder={field.placeholder ?? `value or \${steps.id.output.field}`}
                  prevStepIds={prevStepIds}
                />
              )}

              {/* textarea â†’ ExprTextarea */}
              {field.type === 'textarea' && (
                <ExprTextarea
                  value={val}
                  onChange={v => setValue(field.name, v)}
                  placeholder={field.placeholder}
                  prevStepIds={prevStepIds}
                />
              )}

              {/* number/slider â€” expression-aware single line */}
              {(field.type === 'number' || field.type === 'slider') && (
                isRef ? (
                  <ExprInput
                    value={val}
                    onChange={v => setValue(field.name, v)}
                    placeholder={`number or \${ref}`}
                    prevStepIds={prevStepIds}
                  />
                ) : (
                  <div className="flex gap-1">
                    <Input
                      type="number"
                      value={val}
                      onChange={e => setValue(field.name, e.target.value)}
                      min={field.min}
                      max={field.max}
                      step={field.step}
                      placeholder={field.placeholder}
                      className="h-7 text-xs flex-1"
                    />
                    <RefPicker prevStepIds={prevStepIds} onInsert={ref => setValue(field.name, ref)} />
                  </div>
                )
              )}

              {/* checkbox â€” native when no ref, expression input when ref */}
              {field.type === 'checkbox' && (
                isRef ? (
                  <ExprInput
                    value={val}
                    onChange={v => setValue(field.name, v)}
                    placeholder={`true/false or \${ref}`}
                    prevStepIds={prevStepIds}
                  />
                ) : (
                  <div className="flex items-center gap-2">
                    <Checkbox
                      checked={val === 'true'}
                      onCheckedChange={v => setValue(field.name, v ? 'true' : 'false')}
                    />
                    <span className="text-[10px] text-muted-foreground flex-1">
                      {field.placeholder || field.label}
                    </span>
                    <RefPicker prevStepIds={prevStepIds} onInsert={ref => setValue(field.name, ref)} />
                  </div>
                )
              )}

              {/* select â€” native when no ref, expression input when ref */}
              {field.type === 'select' && field.options && (
                isRef ? (
                  <ExprInput
                    value={val}
                    onChange={v => setValue(field.name, v)}
                    placeholder={`option or \${ref}`}
                    prevStepIds={prevStepIds}
                  />
                ) : (
                  <div className="flex gap-1">
                    <Select value={val} onValueChange={v => setValue(field.name, v)}>
                      <SelectTrigger className="h-7 text-xs flex-1">
                        <SelectValue placeholder={field.placeholder} />
                      </SelectTrigger>
                      <SelectContent>
                        {field.options.map(opt => (
                          <SelectItem key={String(opt.value)} value={String(opt.value)}>
                            {opt.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <RefPicker prevStepIds={prevStepIds} onInsert={ref => setValue(field.name, ref)} />
                  </div>
                )
              )}
            </div>
          );
        })}
      </div>
    );
  }

  // â”€â”€ Free-mapping mode (no form definition) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  return (
    <div className="space-y-1.5 pt-1">
      {inputMapping.map((row, idx) => (
        <div key={idx} className="flex items-center gap-1">
          <Input
            value={row.key}
            onChange={e => updateRow(idx, 'key', e.target.value)}
            placeholder="field"
            className="w-28 h-7 text-xs font-mono shrink-0"
          />
          <span className="text-[10px] text-muted-foreground shrink-0">â†’</span>
          <div className="flex gap-1 flex-1">
            <Input
              value={row.expression}
              onChange={e => updateRow(idx, 'expression', e.target.value)}
              placeholder={`value or \${steps.id.output.field}`}
              className="h-7 text-xs font-mono flex-1"
            />
            <RefPicker
              prevStepIds={prevStepIds}
              onInsert={ref => updateRow(idx, 'expression', row.expression + ref)}
            />
          </div>
          <Button variant="ghost" size="icon" className="h-6 w-6 shrink-0" onClick={() => removeRow(idx)}>
            <Trash2 className="w-3 h-3 text-destructive" />
          </Button>
        </div>
      ))}
      <Button
        variant="ghost"
        size="sm"
        className="h-6 text-[10px] gap-1 text-muted-foreground hover:text-foreground"
        onClick={addRow}
      >
        <Plus className="w-3 h-3" /> Add mapping
      </Button>
    </div>
  );
}

