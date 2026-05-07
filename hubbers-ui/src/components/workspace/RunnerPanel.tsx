import { useState, useMemo, useRef } from 'react';
import {
  Terminal, Settings2, Activity, RefreshCw, Brain, ChevronDown, ChevronUp,
  Wand2, Upload, Download, Copy, Trash2, Play,
} from 'lucide-react';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from '@/components/ui/resizable';
import { toast } from 'sonner';
import type { Artifact } from '@/types/workspace';

// ── Schema-based input generator ─────────────────────────────────────────────

type JsonVal = string | number | boolean | null | JsonVal[] | Record<string, JsonVal>;

function typeDefault(type: string): JsonVal {
  switch (type.toLowerCase()) {
    case 'number': case 'integer': return 0;
    case 'boolean': return false;
    case 'array': return [];
    case 'object': return {};
    default: return '';
  }
}

function parsePropertiesBlock(lines: string[], propertiesIdx: number): Record<string, JsonVal> {
  const result: Record<string, JsonVal> = {};
  const propIndent = lines[propertiesIdx].search(/\S/);
  const fieldIndent = propIndent + 2;

  let field: string | null = null;
  let fieldType = 'string';
  let enumVals: string[] = [];
  let inEnum = false;

  const flush = () => {
    if (!field) return;
    result[field] = enumVals.length > 0 ? enumVals[0] : typeDefault(fieldType);
  };

  for (let i = propertiesIdx + 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line.trim()) continue;
    const indent = line.search(/\S/);
    if (indent <= propIndent) break;

    if (indent === fieldIndent) {
      flush();
      const m = line.trim().match(/^(\w[\w_-]*)\s*:/);
      if (m) { field = m[1]; fieldType = 'string'; enumVals = []; inEnum = false; }
    } else if (indent > fieldIndent && field) {
      const t = line.trim();
      if (t.startsWith('enum:')) {
        inEnum = true;
        const inline = t.match(/^enum:\s*\[(.+)\]/);
        if (inline) {
          enumVals = inline[1].split(',').map(s => s.trim().replace(/^['"]|['"]$/g, ''));
          inEnum = false;
        }
      } else if (inEnum && t.startsWith('- ')) {
        enumVals.push(t.slice(2).trim().replace(/^['"]|['"]$/g, ''));
      } else {
        inEnum = false;
        const kv = t.match(/^type:\s*(.+)$/);
        if (kv) fieldType = kv[1].trim();
      }
    }
  }
  flush();
  return result;
}

export function generateInputFromManifest(yaml: string): string {
  try {
    const lines = yaml.split('\n');

    // Locate top-level `input:` key
    let inputIdx = -1;
    for (let i = 0; i < lines.length; i++) {
      if (/^input\s*:/.test(lines[i])) { inputIdx = i; break; }
    }
    if (inputIdx === -1) return '{}';

    // Collect the input block
    const block: string[] = [];
    for (let i = inputIdx + 1; i < lines.length; i++) {
      if (lines[i].length > 0 && !/^[\s#]/.test(lines[i])) break;
      block.push(lines[i]);
    }

    // Pipeline style: `- paramName`
    const listParams = block.flatMap(l => {
      const m = l.match(/^\s+-\s+(\w[\w_-]*)\s*$/);
      return m ? [m[1]] : [];
    });
    if (listParams.length > 0) {
      const obj: Record<string, JsonVal> = {};
      listParams.forEach(p => { obj[p] = ''; });
      return JSON.stringify(obj, null, 2);
    }

    // Find `properties:` within the block
    for (let i = 0; i < block.length; i++) {
      if (block[i].trim() === 'properties:') {
        const props = parsePropertiesBlock(block, i);
        return Object.keys(props).length > 0 ? JSON.stringify(props, null, 2) : '{}';
      }
    }
    return '{}';
  } catch {
    return '{}';
  }
}

// ── Component ─────────────────────────────────────────────────────────────────

interface RunnerPanelProps {
  runInput: string;
  onRunInputChange: (v: string) => void;
  runOutput: string;
  running: boolean;
  /** YAML manifest of the selected artifact, used for schema-based generation. */
  manifest?: string;
  /** Callback to trigger execution. */
  onRun?: () => void;
  /** Currently selected artifact, used to name downloaded files. */
  selected?: Artifact | null;
}

export function RunnerPanel({ runInput, onRunInputChange, runOutput, running, manifest, onRun, selected }: RunnerPanelProps) {
  const [reasoningOpen, setReasoningOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isValidJson = useMemo(() => {
    const t = runInput.trim();
    if (!t || t === '{}') return true;
    try { JSON.parse(t); return true; } catch { return false; }
  }, [runInput]);

  const parsed = useMemo(() => {
    if (!runOutput) return null;
    try {
      const obj = JSON.parse(runOutput);
      if (obj.reasoning || obj.tools_used) return obj;
    } catch { /* not structured agent output */ }
    return null;
  }, [runOutput]);

  const resultStr = useMemo(() => {
    if (!parsed) return runOutput;
    const { reasoning, tools_used, ...rest } = parsed;
    const display = rest.result !== undefined ? rest.result : rest;
    return typeof display === 'string' ? display : JSON.stringify(display, null, 2);
  }, [parsed, runOutput]);

  // ── Actions ─────────────────────────────────────────────────────────────────

  const handleGenerate = () => {
    if (!manifest) { toast.error('No manifest loaded'); return; }
    const json = generateInputFromManifest(manifest);
    if (json === '{}') {
      toast('No input schema found', { description: 'The manifest has no defined input properties.' });
    } else {
      onRunInputChange(json);
      toast.success('Input generated from schema');
    }
  };

  const handleImport = () => fileInputRef.current?.click();

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
      const text = ev.target?.result as string;
      try { JSON.parse(text); onRunInputChange(text); toast.success(`Loaded ${file.name}`); }
      catch { toast.error('Invalid JSON file'); }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const downloadJson = (content: string, filename: string) => {
    const blob = new Blob([content], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = Object.assign(document.createElement('a'), { href: url, download: filename });
    a.click();
    URL.revokeObjectURL(url);
  };

  const baseName = selected?.name ?? 'artifact';

  return (
    <div className="h-full flex flex-col bg-card">
      <div className="px-4 py-3 border-b flex items-center">
        <span className="flex items-center gap-2 text-sm font-medium">
          <Terminal className="w-4 h-4" />
          Runner & Logs
        </span>
      </div>

      <ResizablePanelGroup direction="vertical" className="flex-1 min-h-0">

        {/* ── Input ─────────────────────────────────────────────────────────── */}
        <ResizablePanel defaultSize={35} minSize={15}>
          <div className="h-full flex flex-col p-3 gap-1.5 min-h-0">

            {/* Toolbar */}
            <div className="flex items-center justify-between gap-1 shrink-0">
              <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground select-none">
                <Settings2 className="w-3 h-3" />
                Input JSON
                {/* validity dot */}
                <span
                  className={`w-1.5 h-1.5 rounded-full ${isValidJson ? 'bg-green-500' : 'bg-red-500'}`}
                  title={isValidJson ? 'Valid JSON' : 'Invalid JSON'}
                />
              </label>

              <div className="flex items-center gap-0.5">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleGenerate} disabled={!manifest}>
                      <Wand2 className="w-3 h-3" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="bottom" className="text-xs">Generate from schema</TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleImport}>
                      <Upload className="w-3 h-3" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="bottom" className="text-xs">Import JSON file</TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-6 w-6"
                      onClick={() => navigator.clipboard.writeText(runInput).then(() => toast.success('Input copied'))}>
                      <Copy className="w-3 h-3" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="bottom" className="text-xs">Copy to clipboard</TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-6 w-6"
                      onClick={() => downloadJson(runInput, `${baseName}-input.json`)}>
                      <Download className="w-3 h-3" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="bottom" className="text-xs">Save input to file</TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => onRunInputChange('{}')}>
                      <Trash2 className="w-3 h-3" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="bottom" className="text-xs">Clear input</TooltipContent>
                </Tooltip>

                {onRun && (
                  <Button
                    size="sm"
                    className="h-6 text-[10px] gap-1 ml-1"
                    onClick={onRun}
                    disabled={running || !isValidJson || !selected}
                  >
                    {running ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Play className="w-3 h-3" />}
                    Run
                  </Button>
                )}
              </div>
            </div>

            {/* Hidden file picker */}
            <input ref={fileInputRef} type="file" accept=".json,application/json" className="hidden" onChange={handleFileChange} />

            <Textarea
              value={runInput}
              onChange={e => onRunInputChange(e.target.value)}
              className={`flex-1 font-mono text-xs resize-none bg-background/50 ${!isValidJson ? 'border-red-500/60' : ''}`}
              placeholder="{}"
            />
          </div>
        </ResizablePanel>

        <ResizableHandle withHandle />

        {/* ── Output ────────────────────────────────────────────────────────── */}
        <ResizablePanel defaultSize={65} minSize={20}>
          <div className="h-full flex flex-col p-3 gap-1.5 min-h-0">

            <div className="flex items-center justify-between gap-1 shrink-0">
              <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground select-none">
                <Activity className="w-3 h-3" />
                Output / Logs
                {running && <RefreshCw className="w-3 h-3 animate-spin" />}
              </label>

              {runOutput && (
                <div className="flex items-center gap-0.5">
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="ghost" size="icon" className="h-6 w-6"
                        onClick={() => navigator.clipboard.writeText(runOutput).then(() => toast.success('Output copied'))}>
                        <Copy className="w-3 h-3" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="bottom" className="text-xs">Copy output</TooltipContent>
                  </Tooltip>

                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="ghost" size="icon" className="h-6 w-6"
                        onClick={() => downloadJson(runOutput, `${baseName}-output.json`)}>
                        <Download className="w-3 h-3" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="bottom" className="text-xs">Save output to file</TooltipContent>
                  </Tooltip>
                </div>
              )}
            </div>

            <pre className="flex-1 overflow-auto font-mono text-xs p-3 rounded-md bg-terminal-bg text-terminal-text border border-primary/20 shadow-inner whitespace-pre-wrap">
              {resultStr || 'No execution output yet.'}
            </pre>

            {parsed?.tools_used && parsed.tools_used.length > 0 && (
              <div className="space-y-1 shrink-0">
                <span className="text-xs font-medium text-muted-foreground">🔧 Tools Used</span>
                <div className="flex flex-wrap gap-1">
                  {parsed.tools_used.map((t: string) => (
                    <Badge key={t} variant="secondary" className="text-[10px] font-mono">{t}</Badge>
                  ))}
                </div>
              </div>
            )}

            {parsed?.reasoning && (
              <Collapsible open={reasoningOpen} onOpenChange={setReasoningOpen} className="shrink-0">
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
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  );
}
