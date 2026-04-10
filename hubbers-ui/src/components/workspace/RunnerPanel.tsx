import { useState, useMemo } from 'react';
import { Terminal, Play, Settings2, Activity, RefreshCw, Brain, ChevronDown, ChevronUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Badge } from '@/components/ui/badge';

interface RunnerPanelProps {
  runInput: string;
  onRunInputChange: (v: string) => void;
  runOutput: string;
  running: boolean;
  disabled: boolean;
  onRun: () => void;
}

export function RunnerPanel({ runInput, onRunInputChange, runOutput, running, disabled, onRun }: RunnerPanelProps) {
  const [reasoningOpen, setReasoningOpen] = useState(false);

  const parsed = useMemo(() => {
    if (!runOutput) return null;
    try {
      const obj = JSON.parse(runOutput);
      if (obj.reasoning || obj.tools_used) return obj;
    } catch { /* not JSON or no agent fields */ }
    return null;
  }, [runOutput]);

  const resultStr = useMemo(() => {
    if (!parsed) return runOutput;
    const { reasoning, tools_used, ...rest } = parsed;
    const display = rest.result !== undefined ? rest.result : rest;
    return typeof display === 'string' ? display : JSON.stringify(display, null, 2);
  }, [parsed, runOutput]);

  return (
    <div className="h-full flex flex-col bg-card">
      <div className="px-4 py-3 border-b flex items-center justify-between">
        <span className="flex items-center gap-2 text-sm font-medium">
          <Terminal className="w-4 h-4" />
          Runner & Logs
        </span>
        <Button size="sm" className="h-7 text-xs gap-1.5" onClick={onRun} disabled={disabled || running}>
          {running ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Play className="w-3 h-3" />}
          Run
        </Button>
      </div>

      <div className="flex-1 flex flex-col p-4 gap-3 min-h-0">
        {/* Input */}
        <div className="space-y-1.5">
          <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <Settings2 className="w-3 h-3" /> Input JSON
          </label>
          <Textarea
            value={runInput}
            onChange={e => onRunInputChange(e.target.value)}
            className="h-32 font-mono text-xs resize-none bg-background/50"
            placeholder="{}"
          />
        </div>

        {/* Output */}
        <div className="flex-1 flex flex-col min-h-0 space-y-1.5">
          <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <Activity className="w-3 h-3" /> Output / Logs
          </label>
          <pre className="flex-1 overflow-auto font-mono text-xs p-3 rounded-md bg-terminal-bg text-terminal-text border border-primary/20 shadow-inner whitespace-pre-wrap">
            {resultStr || 'No execution output yet.'}
          </pre>
        </div>

        {/* Tools used */}
        {parsed?.tools_used && parsed.tools_used.length > 0 && (
          <div className="space-y-1">
            <span className="text-xs font-medium text-muted-foreground">🔧 Tools Used</span>
            <div className="flex flex-wrap gap-1">
              {parsed.tools_used.map((t: string) => (
                <Badge key={t} variant="secondary" className="text-[10px] font-mono">{t}</Badge>
              ))}
            </div>
          </div>
        )}

        {/* Reasoning */}
        {parsed?.reasoning && (
          <Collapsible open={reasoningOpen} onOpenChange={setReasoningOpen}>
            <CollapsibleTrigger className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground">
              {reasoningOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
              <Brain className="w-3 h-3" />
              Reasoning
            </CollapsibleTrigger>
            <CollapsibleContent>
              <p className="mt-2 text-xs text-muted-foreground whitespace-pre-wrap leading-relaxed p-3 rounded-md bg-muted/50 border">
                {parsed.reasoning}
              </p>
            </CollapsibleContent>
          </Collapsible>
        )}
      </div>
    </div>
  );
}
