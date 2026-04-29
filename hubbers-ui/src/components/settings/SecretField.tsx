import { useState } from 'react';
import { EyeOff, KeyRound } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';

interface SecretFieldProps {
  id: string;
  label: string;
  configured?: boolean;
  placeholder?: string;
  onChange: (value: string) => void;
  description?: string;
}

export function SecretField({ id, label, configured, placeholder, onChange, description }: SecretFieldProps) {
  const [value, setValue] = useState('');

  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <KeyRound className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id={id}
            type="password"
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
              onChange(event.target.value);
            }}
            placeholder={configured ? 'Configured - enter a new value to replace' : placeholder || 'Enter secret'}
            className="pl-9"
            autoComplete="new-password"
          />
        </div>
        <Button type="button" variant="outline" size="icon" disabled title="Secrets are write-only in this UI">
          <EyeOff className="h-4 w-4" />
        </Button>
      </div>
      {description && <p className="text-sm text-muted-foreground">{description}</p>}
    </div>
  );
}