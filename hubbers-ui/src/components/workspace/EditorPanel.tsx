import { useState } from 'react';
import { Activity, Save, RefreshCw, Plus, Trash2, Workflow, History, ArrowLeft, Play, ChevronDown, ChevronUp, Pen, RotateCcw, LogIn, Globe } from 'lucide-react';
import { StepInputPanel } from '@/components/workspace/StepInputPanel';
import { AgentBuilder } from '@/components/workspace/AgentBuilder';
import { SkillBuilder } from '@/components/workspace/SkillBuilder';
import { ToolWizard } from '@/components/workspace/ToolWizard';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { Artifact, ArtifactType, Execution, Step, PipelineStep, RepoModel, StepInputMapping, ValidationResult, ArtifactStatus } from '@/types/workspace';
import type { BrunoFileData } from '@/lib/api';
import { toast } from 'sonner';
import * as api from '@/lib/api';

interface EditorPanelProps {
  selected: Artifact | null;
  manifest: string;
  onManifestChange: (v: string) => void;
  editorTab: string;
  onEditorTabChange: (v: string) => void;
  loading: { save: boolean; validate: boolean; manifest: boolean; run: boolean };
  onSave: () => void;
  onValidate: () => void;
  onRun: () => void;
  runDisabled: boolean;
  validationResult: ValidationResult | null;
  artifactStatus: ArtifactStatus | null;
  // Pipeline
  repo: RepoModel;
  pipelineSteps: PipelineStep[];
  onPipelineStepsChange: (steps: PipelineStep[]) => void;
  onSyncSteps: () => void;
  // Executions
  executions: Execution[];
  onLoadExecutions: () => void;
  selectedExecution: string | null;
  executionDetail: { log?: string; input?: any; output?: any; steps?: Step[]; execution?: Execution } | null;
  execDetailTab: string;
  onExecDetailTabChange: (v: string) => void;
  onSelectExecution: (id: string) => void;
  onBackToList: () => void;
  onRerunWithInput?: (input: any) => void;
  onLoadInput?: (input: any) => void;
  brunoFile?: BrunoFileData | null;
}

function statusBadgeVariant(status: string): 'default' | 'destructive' | 'secondary' {
  if (status === 'SUCCESS') return 'default';
  if (status === 'FAILED') return 'destructive';
  return 'secondary';
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleString();
}

export function EditorPanel({
  selected, manifest, onManifestChange, editorTab, onEditorTabChange,
  loading, onSave, onValidate, onRun, runDisabled,
  validationResult, artifactStatus,
  repo, pipelineSteps, onPipelineStepsChange, onSyncSteps,
  executions, onLoadExecutions, selectedExecution, executionDetail, execDetailTab, onExecDetailTabChange,
  onSelectExecution, onBackToList, onRerunWithInput, onLoadInput,
  brunoFile,
}: EditorPanelProps) {
  const [expandedInputs, setExpandedInputs] = useState<Set<number>>(new Set());
  const [brunoTab, setBrunoTab] = useState('overview');

  /** Fetch execution input and re-run (used from the execution list card). */
  const handleRerunFromList = async (executionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const input = await api.fetchExecutionInput(executionId);
      onRerunWithInput?.(input);
    } catch {
      toast.error('Failed to load execution input');
    }
  };

  const toggleInput = (idx: number) => {
    setExpandedInputs(prev => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx); else next.add(idx);
      return next;
    });
  };

  const artifactOptions = (type: ArtifactType): string[] => {
    if (type === 'tool') return repo.tools.map(a => a.name);
    if (type === 'agent') return repo.agents.map(a => a.name);
    if (type === 'pipeline') return repo.pipelines.map(a => a.name);
    if (type === 'skill') return repo.skills.map(a => a.name);
    return [];
  };
  if (!selected && !brunoFile) {
    return (
      <div className="h-full flex items-center justify-center text-muted-foreground text-sm">
        Select an artifact to begin editing
      </div>
    );
  }

  if (brunoFile && !selected) {
    const METHOD_COLORS: Record<string, string> = {
      GET: 'bg-blue-500/20 text-blue-700 dark:text-blue-300 border-blue-500/30',
      POST: 'bg-green-500/20 text-green-700 dark:text-green-300 border-green-500/30',
      PUT: 'bg-yellow-500/20 text-yellow-700 dark:text-yellow-300 border-yellow-500/30',
      PATCH: 'bg-orange-500/20 text-orange-700 dark:text-orange-300 border-orange-500/30',
      DELETE: 'bg-red-500/20 text-red-700 dark:text-red-300 border-red-500/30',
    };
    const methodColor = METHOD_COLORS[brunoFile.method] ?? 'bg-muted text-foreground';
    return (
      <div className="h-full flex flex-col bg-card">
        {/* Header */}
        <div className="px-4 py-3 border-b space-y-1">
          <p className="text-[10px] font-mono text-muted-foreground truncate">
            bruno / {brunoFile.project} / {brunoFile.path}
          </p>
          <div className="flex items-center gap-2">
            <Globe className="w-3.5 h-3.5 text-orange-500 shrink-0" />
            <p className="text-sm font-medium truncate">{brunoFile.name}</p>
            <Badge variant="outline" className={`text-[10px] shrink-0 border ${methodColor}`}>
              {brunoFile.method}
            </Badge>
          </div>
        </div>

        {/* Tabs */}
        <Tabs value={brunoTab} onValueChange={setBrunoTab} className="flex-1 flex flex-col min-h-0">
          <TabsList className="mx-4 mt-2 w-fit h-7">
            <TabsTrigger value="overview" className="text-xs h-6 px-2">Overview</TabsTrigger>
            <TabsTrigger value="raw" className="text-xs h-6 px-2">Raw YAML</TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="flex-1 min-h-0 m-0">
            <ScrollArea className="h-full p-4">
              <div className="space-y-4">
                {/* URL */}
                <Card className="border-l-[3px] border-l-orange-500">
                  <CardHeader className="py-2 px-3">
                    <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground">REQUEST</CardTitle>
                  </CardHeader>
                  <CardContent className="px-3 pb-3">
                    <div className="flex items-center gap-2 font-mono text-xs bg-muted/40 rounded-md px-3 py-2 break-all">
                      <span className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-bold border ${methodColor}`}>
                        {brunoFile.method}
                      </span>
                      <span className="text-foreground">{brunoFile.url || '—'}</span>
                    </div>
                    {brunoFile.auth && brunoFile.auth !== 'inherit' && brunoFile.auth !== '' && (
                      <p className="mt-2 text-[10px] text-muted-foreground">Auth: <span className="text-foreground font-mono">{brunoFile.auth}</span></p>
                    )}
                    {brunoFile.auth === 'inherit' && (
                      <p className="mt-2 text-[10px] text-muted-foreground">Auth: <span className="text-foreground font-mono">inherited from collection</span></p>
                    )}
                  </CardContent>
                </Card>

                {/* Params */}
                {brunoFile.params.length > 0 && (
                  <Card>
                    <CardHeader className="py-2 px-3">
                      <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground">PARAMETERS</CardTitle>
                    </CardHeader>
                    <CardContent className="px-0 pb-2">
                      <Table>
                        <TableHeader>
                          <TableRow className="text-[10px]">
                            <TableHead className="h-7 text-[10px] pl-3">Name</TableHead>
                            <TableHead className="h-7 text-[10px]">Type</TableHead>
                            <TableHead className="h-7 text-[10px]">Value</TableHead>
                            <TableHead className="h-7 text-[10px] pr-3">Description</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {brunoFile.params.map((p, i) => (
                            <TableRow key={i} className="text-xs">
                              <TableCell className="font-mono pl-3 py-1.5">{p.name}</TableCell>
                              <TableCell className="py-1.5">
                                <Badge variant="secondary" className="text-[9px] px-1 py-0 h-4">{p.type}</Badge>
                              </TableCell>
                              <TableCell className="font-mono py-1.5 text-muted-foreground">{p.value || '—'}</TableCell>
                              <TableCell className="py-1.5 text-muted-foreground pr-3">{p.description || '—'}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </CardContent>
                  </Card>
                )}

                {/* Headers */}
                {brunoFile.headers.length > 0 && (
                  <Card>
                    <CardHeader className="py-2 px-3">
                      <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground">HEADERS</CardTitle>
                    </CardHeader>
                    <CardContent className="px-0 pb-2">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead className="h-7 text-[10px] pl-3">Name</TableHead>
                            <TableHead className="h-7 text-[10px] pr-3">Value</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {brunoFile.headers.map((h, i) => (
                            <TableRow key={i} className="text-xs">
                              <TableCell className="font-mono pl-3 py-1.5">{h.name}</TableCell>
                              <TableCell className="font-mono py-1.5 text-muted-foreground pr-3">{h.value}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </CardContent>
                  </Card>
                )}

                {/* Body */}
                {brunoFile.body && (
                  <Card>
                    <CardHeader className="py-2 px-3">
                      <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground">
                        BODY <Badge variant="secondary" className="ml-1 text-[9px] px-1 py-0 h-4">{brunoFile.body.type}</Badge>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="px-3 pb-3">
                      <pre className="text-xs font-mono bg-muted/40 rounded-md p-3 whitespace-pre-wrap break-all">{brunoFile.body.data || '—'}</pre>
                    </CardContent>
                  </Card>
                )}
              </div>
            </ScrollArea>
          </TabsContent>

          <TabsContent value="raw" className="flex-1 min-h-0 m-0">
            <Textarea
              value={brunoFile.raw}
              readOnly
              className="h-full resize-none rounded-none border-0 font-mono text-xs bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0"
            />
          </TabsContent>
        </Tabs>
      </div>
    );
  }

  const addStep = () => {
    onPipelineStepsChange([...pipelineSteps, { id: `step_${pipelineSteps.length + 1}`, targetType: 'tool', target: '', inputMapping: [] }]);
  };

  const removeStep = (idx: number) => {
    setExpandedInputs(prev => {
      const next = new Set(prev);
      next.delete(idx);
      return next;
    });
    onPipelineStepsChange(pipelineSteps.filter((_, i) => i !== idx));
  };

  const updateStep = (idx: number, field: keyof PipelineStep, value: string) => {
    const updated = [...pipelineSteps];
    updated[idx] = { ...updated[idx], [field]: value };
    onPipelineStepsChange(updated);
  };

  const updateStepType = (idx: number, newType: string) => {
    const updated = [...pipelineSteps];
    updated[idx] = { ...updated[idx], targetType: newType as ArtifactType, target: '', inputMapping: [] };
    onPipelineStepsChange(updated);
  };

  const updateStepMapping = (idx: number, mapping: StepInputMapping[]) => {
    const updated = [...pipelineSteps];
    updated[idx] = { ...updated[idx], inputMapping: mapping };
    onPipelineStepsChange(updated);
  };

  const loadStepDetail = async (executionId: string, stepName: string, type: 'input' | 'output' | 'log') => {
    try {
      let data: any;
      if (type === 'log') data = await api.fetchStepLog(executionId, stepName);
      else if (type === 'input') data = await api.fetchStepInput(executionId, stepName);
      else data = await api.fetchStepOutput(executionId, stepName);
      toast(stepName + ' ' + type, { description: typeof data === 'string' ? data : JSON.stringify(data, null, 2) });
    } catch (e: any) {
      toast.error(e.message);
    }
  };

  const designerLabel = selected.type === 'pipeline'
    ? 'Pipeline Designer'
    : selected.type === 'skill'
      ? 'Skill Designer'
      : 'Visual Builder';

  return (
    <Tabs value={editorTab} onValueChange={onEditorTabChange} className="h-full flex flex-col bg-card min-h-0">
      {/* Header */}
      <div className="px-4 py-3 border-b space-y-1">
        <p className="text-[10px] font-mono text-muted-foreground truncate">{selected.path}</p>
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <p className="text-sm font-medium truncate">{selected.type} / {selected.name}</p>
            <Badge variant={artifactStatus?.valid === false ? 'destructive' : artifactStatus?.valid ? 'default' : 'secondary'} className="text-[10px] shrink-0">
              {artifactStatus?.status || 'draft'}
            </Badge>
            {artifactStatus?.certified && <Badge variant="outline" className="text-[10px] shrink-0">certified</Badge>}
          </div>
          <div className="flex items-center gap-1.5 shrink-0">
            <TabsList className="h-7">
              <TabsTrigger value="yaml" className="text-xs h-6 px-2">
                {selected.type === 'skill' ? 'Markdown Editor' : 'YAML Editor'}
              </TabsTrigger>
              <TabsTrigger value="designer" className="text-xs h-6 px-2">
                {designerLabel}
              </TabsTrigger>
              <TabsTrigger value="executions" className="text-xs h-6 px-2">Executions</TabsTrigger>
            </TabsList>
            <Separator orientation="vertical" className="h-5 mx-0.5" />
            <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" onClick={onValidate} disabled={loading.validate}>
              {loading.validate ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Activity className="w-3 h-3" />}
              Validate
            </Button>
            <Button size="sm" className="h-7 text-xs gap-1.5" onClick={onSave} disabled={loading.save}>
              {loading.save ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Save className="w-3 h-3" />}
              Save
            </Button>
            <Button size="sm" className="h-7 text-xs gap-1.5" onClick={onRun} disabled={runDisabled || loading.run}>
              {loading.run ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Play className="w-3 h-3" />}
              Run
            </Button>
          </div>
        </div>
        {validationResult && !validationResult.valid && (
          <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            <p className="font-medium">Validation failed</p>
            <ul className="mt-1 list-disc pl-4 space-y-0.5">
              {(validationResult.errors || []).map(error => <li key={error}>{error}</li>)}
            </ul>
          </div>
        )}
        {validationResult?.valid && (
          <div className="rounded-md border border-green-500/30 bg-green-500/10 px-3 py-2 text-xs text-green-700 dark:text-green-300">
            Manifest validation passed.
          </div>
        )}
      </div>

      {/* YAML / Markdown Editor */}
      <TabsContent value="yaml" className="flex-1 min-h-0 m-0 p-0">
        <Textarea
          value={manifest}
          onChange={e => onManifestChange(e.target.value)}
          placeholder="Select an artifact to edit..."
          className="h-full resize-none rounded-none border-0 font-mono text-xs bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0"
        />
      </TabsContent>

      {/* Designer: Visual Builder / Pipeline Designer / Skill Designer */}
      <TabsContent value="designer" className="flex-1 min-h-0 m-0">
        {selected.type === 'agent' && (
          <AgentBuilder manifest={manifest} onManifestChange={onManifestChange} repo={repo} />
        )}
        {selected.type === 'skill' && (
          <SkillBuilder manifest={manifest} onManifestChange={onManifestChange} />
        )}
        {selected.type === 'tool' && (
          <ToolWizard manifest={manifest} onManifestChange={onManifestChange} />
        )}
        {selected.type === 'pipeline' && (
          <div className="flex-1 flex flex-col min-h-0">
            <div className="p-3 border-b flex gap-2">
              <Button variant="outline" size="sm" className="h-7 text-xs gap-1" onClick={addStep}>
                <Plus className="w-3 h-3" /> Add Step
              </Button>
              <Button variant="outline" size="sm" className="h-7 text-xs gap-1" onClick={onSyncSteps}>
                <RefreshCw className="w-3 h-3" /> Sync to YAML
              </Button>
            </div>
            <ScrollArea className="flex-1 p-3">
            {pipelineSteps.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
                <Workflow className="w-8 h-8 mb-2 opacity-40" />
                <p className="text-xs">No steps defined yet.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {pipelineSteps.map((step, idx) => (
                  <Card key={idx} className="border-l-[3px] border-l-primary hover:shadow-md transition-shadow">
                    <CardHeader className="py-2 px-3 flex flex-row items-center justify-between">
                      <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground">STEP {idx + 1}</CardTitle>
                      <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => removeStep(idx)}>
                        <Trash2 className="w-3 h-3 text-destructive" />
                      </Button>
                    </CardHeader>
                    <CardContent className="py-2 px-3 space-y-2">
                      <div className="grid grid-cols-3 gap-2">
                        <Input value={step.id} onChange={e => updateStep(idx, 'id', e.target.value)} placeholder="Step name" className="h-7 text-xs" />
                        <Select value={step.targetType} onValueChange={v => updateStepType(idx, v)}>
                          <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                          <SelectContent>
                            <SelectItem value="tool">Tool</SelectItem>
                            <SelectItem value="agent">Agent</SelectItem>
                            <SelectItem value="pipeline">Pipeline</SelectItem>
                            <SelectItem value="skill">Skill</SelectItem>
                          </SelectContent>
                        </Select>
                        <Select value={step.target} onValueChange={v => updateStep(idx, 'target', v)}>
                          <SelectTrigger className="h-7 text-xs">
                            <SelectValue placeholder="Select artifact..." />
                          </SelectTrigger>
                          <SelectContent>
                            {artifactOptions(step.targetType).map(name => (
                              <SelectItem key={name} value={name}>{name}</SelectItem>
                            ))}
                            {artifactOptions(step.targetType).length === 0 && (
                              <div className="px-2 py-1.5 text-xs text-muted-foreground">No artifacts found</div>
                            )}
                          </SelectContent>
                        </Select>
                      </div>
                      <button
                        className="w-full flex items-center justify-between text-[10px] font-semibold text-muted-foreground hover:text-foreground transition-colors pt-1 border-t"
                        onClick={() => toggleInput(idx)}
                      >
                        <span>INPUT</span>
                        {expandedInputs.has(idx) ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                      </button>
                      {expandedInputs.has(idx) && (
                        <StepInputPanel
                          targetType={step.targetType}
                          targetName={step.target}
                          inputMapping={step.inputMapping}
                          onInputMappingChange={mapping => updateStepMapping(idx, mapping)}
                          prevStepIds={pipelineSteps.slice(0, idx).map(s => s.id).filter(Boolean)}
                        />
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
            </ScrollArea>
          </div>
        )}
      </TabsContent>

      {/* Executions */}
      <TabsContent value="executions" className="flex-1 min-h-0 m-0">
          {selectedExecution && executionDetail ? (
            <div className="h-full flex flex-col">
              <div className="px-3 py-2 border-b flex items-center gap-2">
                <Button variant="ghost" size="sm" className="h-6 text-xs gap-1" onClick={onBackToList}>
                  <ArrowLeft className="w-3 h-3" /> Back
                </Button>
                <code className="text-xs text-primary font-mono">{selectedExecution}</code>
                {executionDetail.execution && <Badge variant={statusBadgeVariant(executionDetail.execution.status)} className="text-[10px]">{executionDetail.execution.status}</Badge>}
              </div>
              <Tabs value={execDetailTab} onValueChange={onExecDetailTabChange} className="flex-1 flex flex-col min-h-0">
                <TabsList className="mx-3 mt-1 w-fit">
                  {['metadata', 'log', 'input', 'output', 'steps'].map(t => (
                    <TabsTrigger key={t} value={t} className="text-[10px] uppercase">{t}</TabsTrigger>
                  ))}
                </TabsList>
                <ScrollArea className="flex-1 p-3">
                  {execDetailTab === 'metadata' && executionDetail.execution && (
                    <pre className="text-xs font-mono bg-muted/30 p-3 rounded-md whitespace-pre-wrap">{JSON.stringify(executionDetail.execution, null, 2)}</pre>
                  )}
                  {execDetailTab === 'log' && (
                    <pre className="text-xs font-mono bg-terminal-bg text-terminal-text p-3 rounded-md whitespace-pre-wrap">{executionDetail.log || 'No log available'}</pre>
                  )}
                  {execDetailTab === 'input' && (
                    <div className="space-y-2">
                      {executionDetail.input != null && (onLoadInput || onRerunWithInput) && (
                        <div className="flex items-center gap-1.5">
                          {onLoadInput && (
                            <Button
                              variant="outline" size="sm" className="h-6 text-[10px] gap-1"
                              onClick={() => { onLoadInput(executionDetail.input); toast.success('Input loaded to runner'); }}
                            >
                              <LogIn className="w-3 h-3" /> Load to Runner
                            </Button>
                          )}
                          {onRerunWithInput && (
                            <Button
                              size="sm" className="h-6 text-[10px] gap-1"
                              onClick={() => onRerunWithInput(executionDetail.input)}
                            >
                              <RotateCcw className="w-3 h-3" /> Re-run
                            </Button>
                          )}
                        </div>
                      )}
                      <pre className="text-xs font-mono bg-muted/30 p-3 rounded-md whitespace-pre-wrap">{JSON.stringify(executionDetail.input, null, 2)}</pre>
                    </div>
                  )}
                  {execDetailTab === 'output' && (
                    <pre className="text-xs font-mono bg-muted/30 p-3 rounded-md whitespace-pre-wrap">{JSON.stringify(executionDetail.output, null, 2)}</pre>
                  )}
                  {execDetailTab === 'steps' && (
                    <div className="space-y-2">
                      {(executionDetail.steps || []).map(step => (
                        <Card key={step.name} className="border-l-[3px] border-l-primary">
                          <CardHeader className="py-2 px-3">
                            <CardTitle className="text-xs font-mono">{step.name}</CardTitle>
                          </CardHeader>
                          <CardContent className="py-2 px-3 flex gap-1">
                            {step.hasInput && <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={() => loadStepDetail(selectedExecution!, step.name, 'input')}>Input</Button>}
                            {step.hasOutput && <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={() => loadStepDetail(selectedExecution!, step.name, 'output')}>Output</Button>}
                            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={() => loadStepDetail(selectedExecution!, step.name, 'log')}>Log</Button>
                          </CardContent>
                        </Card>
                      ))}
                      {(!executionDetail.steps || executionDetail.steps.length === 0) && (
                        <p className="text-xs text-muted-foreground text-center py-4">No steps found</p>
                      )}
                    </div>
                  )}
                </ScrollArea>
              </Tabs>
            </div>
          ) : (
            <div className="h-full flex flex-col">
              <div className="px-3 py-2 border-b flex items-center justify-between">
                <span className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">Execution History</span>
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={onLoadExecutions}>
                  <RefreshCw className="w-3 h-3" />
                </Button>
              </div>
              <ScrollArea className="flex-1 p-2">
                {executions.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
                    <History className="w-8 h-8 mb-2 opacity-40" />
                    <p className="text-xs">No executions found</p>
                  </div>
                ) : (
                  <div className="space-y-1.5">
                    {executions.map(exec => (
                      <Card key={exec.executionId} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onSelectExecution(exec.executionId)}>
                        <CardContent className="p-3 space-y-1">
                          <div className="flex items-center justify-between gap-1">
                            <code className="text-xs text-primary font-mono truncate flex-1 min-w-0">{exec.executionId}</code>
                            <div className="flex items-center gap-1 shrink-0">
                              <Badge variant={statusBadgeVariant(exec.status)} className="text-[10px]">{exec.status}</Badge>
                              {onRerunWithInput && (
                                <Button
                                  variant="ghost" size="icon" className="h-5 w-5"
                                  title="Re-run with same input"
                                  onClick={e => handleRerunFromList(exec.executionId, e)}
                                >
                                  <RotateCcw className="w-3 h-3" />
                                </Button>
                              )}
                            </div>
                          </div>
                          <div className="flex items-center justify-between text-[10px] text-muted-foreground">
                            <span>{exec.artifactType}/{exec.artifactName}</span>
                            <span>{formatTime(exec.startedAt)}{exec.endedAt ? ` • ${((exec.endedAt - exec.startedAt) / 1000).toFixed(1)}s` : ''}</span>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                )}
              </ScrollArea>
            </div>
          )}
      </TabsContent>
    </Tabs>
  );
}
