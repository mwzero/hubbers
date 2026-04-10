import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import type { FormDef } from '@/types/workspace';

interface FormModalProps {
  open: boolean;
  form?: FormDef;
  data: Record<string, any>;
  onDataChange: (data: Record<string, any>) => void;
  onSubmit: () => void;
  onCancel: () => void;
}

export function FormModal({ open, form, data, onDataChange, onSubmit, onCancel }: FormModalProps) {
  if (!form) return null;

  const updateField = (name: string, value: any) => {
    onDataChange({ ...data, [name]: value });
  };

  return (
    <Dialog open={open} onOpenChange={v => { if (!v) onCancel(); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          {form.title && <DialogTitle>{form.title}</DialogTitle>}
          {form.description && <DialogDescription>{form.description}</DialogDescription>}
        </DialogHeader>
        <div className="space-y-4 py-2">
          {form.fields.map(field => (
            <div key={field.name} className="space-y-1.5">
              <Label className="text-xs">
                {field.label || field.name}
                {field.required && <span className="text-destructive ml-0.5">*</span>}
              </Label>
              {field.type === 'text' && (
                <Input
                  value={data[field.name] ?? ''}
                  onChange={e => updateField(field.name, e.target.value)}
                  placeholder={field.placeholder}
                  className="h-8 text-xs"
                />
              )}
              {field.type === 'textarea' && (
                <Textarea
                  value={data[field.name] ?? ''}
                  onChange={e => updateField(field.name, e.target.value)}
                  placeholder={field.placeholder}
                  className="text-xs"
                />
              )}
              {(field.type === 'number' || field.type === 'slider') && (
                <Input
                  type="number"
                  value={data[field.name] ?? ''}
                  onChange={e => updateField(field.name, Number(e.target.value))}
                  min={field.min}
                  max={field.max}
                  step={field.step}
                  className="h-8 text-xs"
                />
              )}
              {field.type === 'checkbox' && (
                <div className="flex items-center gap-2">
                  <Checkbox
                    checked={!!data[field.name]}
                    onCheckedChange={v => updateField(field.name, v)}
                  />
                  <span className="text-xs text-muted-foreground">{field.placeholder || field.label}</span>
                </div>
              )}
              {field.type === 'select' && field.options && (
                <Select value={String(data[field.name] ?? '')} onValueChange={v => updateField(field.name, v)}>
                  <SelectTrigger className="h-8 text-xs"><SelectValue placeholder={field.placeholder} /></SelectTrigger>
                  <SelectContent>
                    {field.options.map(opt => (
                      <SelectItem key={String(opt.value)} value={String(opt.value)}>{opt.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" size="sm" onClick={onCancel}>Cancel</Button>
          <Button size="sm" onClick={onSubmit}>Submit</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
