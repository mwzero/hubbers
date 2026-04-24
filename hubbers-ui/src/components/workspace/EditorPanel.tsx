import { useState } from 'react';
import { Activity, Save, RefreshCw, Plus, Trash2, Workflow, History, ArrowLeft, Play, ChevronDown, ChevronUp } from 'lucide-react';
import { StepInputPanel } from '@/components/workspace/StepInputPanel';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import type { Artifact, ArtifactType, Execution, Step, PipelineStep, RepoModel, StepInputMapping } from '@/types/workspace';
import { toast } from 'sonner';
import * as api from '@/lib/api';

function SkillPreview({ manifest }: { manifest: string }) {
  const sections = manifest.split(/^## /m).filter(Boolean);
  const parsed: { metadata?: any; model?: any; instructions?: string } = {};

  for (const section of sections) {
    const jsonMatch = section.match(/```json\s*\n([\s\S]*?)```/);
    if (section.startsWith('Metadata') && jsonMatch) {
      try { parsed.metadata = JSON.parse(jsonMatch[1]); } catch {}
    } else if (section.startsWith('Model') && jsonMatch) {
      try { parsed.model = JSON.parse(jsonMatch[1]); } catch {}
    } else if (section.startsWith('Instructions')) {
      parsed.instructions = section.replace(/^Instructions\s*\n/, '').trim();
    }
  }

  return (
    <div className="space-y-4">
      {parsed.metadata && (
        <Card>
          <CardHeader className="py-3 px-4">
            <CardTitle className="text-xs font-bold tracking-widest text-muted-foreground uppercase">Metadata</CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3 space-y-1.5">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold">{parsed.metadata.name}</span>
              {parsed.metadata.version && <Badge variant="secondary" className="text-[10px]">v{parsed.metadata.version}</Badge>}
            </div>
            {parsed.metadata.description && <p className="text-xs text-muted-foreground">{parsed.metadata.description}</p>}
            {parsed.metadata.tags && (
              <div className="flex gap-1 flex-wrap">
                {parsed.metadata.tags.map((t: string) => <Badge key={t} variant="outline" className="text-[10px] text-skill">{t}</Badge>)}
              </div>
            )}
            {parsed.metadata.author && <p className="text-[10px] text-muted-foreground">Author: {parsed.metadata.author}</p>}
          </CardContent>
        </Card>
      )}
      {parsed.model && (
        <Card>
          <CardHeader className="py-3 px-4">
            <CardTitle className="text-xs font-bold tracking-widest text-muted-foreground uppercase">Model</CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3">
            <pre className="text-xs font-mono bg-muted/30 p-3 rounded-md">{JSON.stringify(parsed.model, null, 2)}</pre>
          </CardContent>
        </Card>
      )}
      {parsed.instructions && (
        <Card>
          <CardHeader className="py-3 px-4">
            <CardTitle className="text-xs font-bold tracking-widest text-muted-foreground uppercase">Instructions</CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-3">
            <pre className="text-xs font-mono whitespace-pre-wrap bg-muted/30 p-3 rounded-md">{parsed.instructions}</pre>
          </CardContent>
        </Card>
      )}
      {!parsed.metadata && !parsed.instructions && (
        <p className="text-xs text-muted-foreground text-center py-8">No parseable SKILL.md content</p>
      )}
    </div>
  );
}

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
  repo, pipelineSteps, onPipelineStepsChange, onSyncSteps,
  executions, onLoadExecutions, selectedExecution, executionDetail, execDetailTab, onExecDetailTabChange,
  onSelectExecution, onBackToList,
}: EditorPanelProps) {
  const [expandedInputs, setExpandedInputs] = useState<Set<number>>(new Set());

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
  if (!selected) {
    return (
      <div className="h-full flex items-center justify-center text-muted-foreground text-sm">
        Select an artifact to begin editing
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

  return (
    <div className="h-full flex flex-col bg-card">
      {/* Header */}
      <div className="px-4 py-3 border-b space-y-1">
        <p className="text-[10px] font-mono text-muted-foreground truncate">{selected.path}</p>
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium">{selected.type} / {selected.name}</p>
          <div className="flex gap-1.5">
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
      </div>

      {/* Tabs */}
      <Tabs value={editorTab} onValueChange={onEditorTabChange} className="flex-1 flex flex-col min-h-0">
        <TabsList className="mx-4 mt-2 w-fit">
          <TabsTrigger value="yaml" className="text-xs">{selected.type === 'skill' ? 'Markdown Editor' : 'YAML Editor'}</TabsTrigger>
          <TabsTrigger value="pipeline" className="text-xs" disabled={selected.type !== 'pipeline'}>Pipeline Designer</TabsTrigger>
          <TabsTrigger value="preview" className="text-xs" disabled={selected.type !== 'skill'}>Preview</TabsTrigger>
          <TabsTrigger value="executions" className="text-xs">Executions</TabsTrigger>
        </TabsList>

        {/* YAML */}
        <TabsContent value="yaml" className="flex-1 min-h-0 m-0 p-0">
          <Textarea
            value={manifest}
            onChange={e => onManifestChange(e.target.value)}
            placeholder="Select an artifact to edit..."
            className="h-full resize-none rounded-none border-0 font-mono text-xs bg-transparent focus-visible:ring-0 focus-visible:ring-offset-0"
          />
        </TabsContent>

        {/* Skill Preview */}
        <TabsContent value="preview" className="flex-1 min-h-0 m-0">
          <ScrollArea className="h-full p-4">
            <SkillPreview manifest={manifest} />
          </ScrollArea>
        </TabsContent>

        {/* Pipeline */}
        <TabsContent value="pipeline" className="flex-1 min-h-0 m-0">
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
                    <pre className="text-xs font-mono bg-muted/30 p-3 rounded-md whitespace-pre-wrap">{JSON.stringify(executionDetail.input, null, 2)}</pre>
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
                          <div className="flex items-center justify-between">
                            <code className="text-xs text-primary font-mono truncate">{exec.executionId}</code>
                            <Badge variant={statusBadgeVariant(exec.status)} className="text-[10px]">{exec.status}</Badge>
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
    </div>
  );
}
