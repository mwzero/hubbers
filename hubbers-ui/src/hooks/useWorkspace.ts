import { useState, useCallback, useEffect, useRef } from 'react';
import type { Artifact, ArtifactType, RepoModel, Execution, Step, PipelineStep, StepInputMapping, FormDef } from '@/types/workspace';
import * as api from '@/lib/api';
import { toast } from 'sonner';

export function useWorkspace() {
  const [repo, setRepo] = useState<RepoModel>({ agents: [], tools: [], pipelines: [], skills: [] });
  const [selected, setSelected] = useState<Artifact | null>(null);
  const [manifest, setManifest] = useState('');
  const [editorTab, setEditorTab] = useState('yaml');
  const [loading, setLoading] = useState({ repo: false, manifest: false, save: false, validate: false, run: false });
  const [apiOnline, setApiOnline] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);

  // Runner
  const [runInput, setRunInput] = useState('{}');
  const [runOutput, setRunOutput] = useState('');

  // Form modal
  const [formModal, setFormModal] = useState<{ open: boolean; form?: FormDef; sessionId?: string; data: Record<string, any> }>({ open: false, data: {} });

  // Executions
  const [executions, setExecutions] = useState<Execution[]>([]);
  const [selectedExecution, setSelectedExecution] = useState<string | null>(null);
  const [executionDetail, setExecutionDetail] = useState<{ log?: string; input?: any; output?: any; steps?: Step[]; execution?: Execution } | null>(null);
  const [execDetailTab, setExecDetailTab] = useState('metadata');

  // Pipeline steps
  const [pipelineSteps, setPipelineSteps] = useState<PipelineStep[]>([]);
  const [pipelineInputParams, setPipelineInputParams] = useState<string[]>([]);

  const healthRef = useRef<ReturnType<typeof setInterval>>();

  // Health check
  useEffect(() => {
    const check = async () => setApiOnline(await api.checkHealth());
    check();
    healthRef.current = setInterval(check, 10000);
    return () => clearInterval(healthRef.current);
  }, []);

  const loadRepo = useCallback(async () => {
    setLoading(l => ({ ...l, repo: true }));
    try {
      const [agents, tools, pipelines, skills] = await Promise.all([
        api.fetchArtifacts('agents'),
        api.fetchArtifacts('tools'),
        api.fetchArtifacts('pipelines'),
        api.fetchArtifacts('skills'),
      ]);
      const toArtifact = (names: string[], type: ArtifactType): Artifact[] =>
        names.map(n => ({ label: n, type, path: type === 'skill' ? `repo/skills/${n}/SKILL.md` : `repo/${type}s/${n}/${type}.yaml`, name: n }));
      setRepo({
        agents: toArtifact(agents, 'agent'),
        tools: toArtifact(tools, 'tool'),
        pipelines: toArtifact(pipelines, 'pipeline'),
        skills: toArtifact(skills, 'skill'),
      });
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setLoading(l => ({ ...l, repo: false }));
    }
  }, []);

  useEffect(() => { loadRepo(); }, [loadRepo]);

  const selectArtifact = useCallback(async (art: Artifact) => {
    setSelected(art);
    setEditorTab('yaml');
    setSelectedExecution(null);
    setExecutionDetail(null);
    setLoading(l => ({ ...l, manifest: true }));
    try {
      const yaml = await api.fetchManifest(art.type, art.name);
      setManifest(yaml);
      if (art.type === 'pipeline') parsePipelineSteps(yaml);
    } catch (e: any) {
      toast.error(e.message);
      setManifest('');
    } finally {
      setLoading(l => ({ ...l, manifest: false }));
    }
  }, []);

  const parsePipelineSteps = (yaml: string) => {
    const steps: PipelineStep[] = [];
    const normalized = yaml.replace(/\t/g, '  ');

    // Parse pipeline-level input params from `input:` list
    const inputSection = normalized.match(/^input:\s*\n((?:[ \t]+-[ \t]+\S+\n?)*)(?=\n|$)/m);
    if (inputSection) {
      const params = [...inputSection[1].matchAll(/^\s+-\s+(\S+)/gm)].map(m => m[1]);
      setPipelineInputParams(params);
    } else {
      setPipelineInputParams([]);
    }

    const match = normalized.match(/steps:\s*\n([\s\S]*?)(?=\n\S|\n*$)/);
    if (match) {
      const block = match[1];
      const stepBlocks = block.split(/\n\s*-\s+/).filter(Boolean);
      for (const sb of stepBlocks) {
        const idMatch = sb.match(/id:\s*(.+)/);
        const targetMatch = sb.match(/(tool|agent|pipeline|skill):\s*(.+)/);
        if (idMatch) {
          const step: PipelineStep = {
            id: idMatch[1].trim(),
            targetType: (targetMatch?.[1] as ArtifactType) || 'tool',
            target: targetMatch?.[2]?.trim() || '',
            inputMapping: [],
          };
          // Parse input_mapping block
          const mappingMatch = sb.match(/input_mapping:\s*\n((?:[ \t]+\S[^\n]*\n?)*)/);
          if (mappingMatch) {
            const mappingLines = mappingMatch[1].split('\n').filter(l => l.trim());
            const minIndent = Math.min(...mappingLines.map(l => (l.match(/^(\s*)/) || ['', ''])[1].length));
            for (const line of mappingLines) {
              const kvMatch = line.slice(minIndent).match(/^([\w._-]+):\s*(.*)$/);
              if (kvMatch) {
                // Strip surrounding double-quotes added by previous versions or present in hand-written YAML
                const raw = kvMatch[2].trim();
                const expression = raw.startsWith('"') && raw.endsWith('"')
                  ? raw.slice(1, -1).replace(/\\"/g, '"')
                  : raw;
                if (expression) step.inputMapping.push({ key: kvMatch[1], expression });
              }
            }
          }
          steps.push(step);
        }
      }
    }
    setPipelineSteps(steps);
  };

  const buildYamlFromSteps = (steps: PipelineStep[]): string => {
    if (steps.length === 0) return 'steps: []\n';
    const lines = ['steps:'];
    for (const s of steps) {
      lines.push(`  - id: ${s.id}`);
      lines.push(`    ${s.targetType}: ${s.target}`);
      const activeMapping = s.inputMapping.filter(m => m.key.trim() && m.expression.trim());
      if (activeMapping.length > 0) {
        lines.push(`    input_mapping:`);
        for (const m of activeMapping) {
          // Use unquoted expressions - matches backend InputMapper expected format
          // ${...} references are valid unquoted YAML plain scalars
          lines.push(`      ${m.key}: ${m.expression}`);
        }
      }
    }
    return lines.join('\n') + '\n';
  };

  const syncStepsToYaml = useCallback(() => {
    const normalized = manifest.replace(/\t/g, '  ');
    const newStepsYaml = buildYamlFromSteps(pipelineSteps);
    // Only replace the steps block; leave the rest of the YAML (schema, input, examples) intact
    const updated = normalized.replace(/steps:[\s\S]*?(?=\n\S|$)/, newStepsYaml.trimEnd());
    setManifest(updated);
    toast.success('Steps synced to YAML');
  }, [manifest, pipelineSteps]);

  const saveManifest = useCallback(async () => {
    if (!selected) return;
    setLoading(l => ({ ...l, save: true }));
    try {
      await api.saveManifest(selected.type, selected.name, manifest);
      toast.success('Manifest saved');
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setLoading(l => ({ ...l, save: false }));
    }
  }, [selected, manifest]);

  const validateManifest = useCallback(async () => {
    if (!selected) return;
    setLoading(l => ({ ...l, validate: true }));
    try {
      const result = await api.validateManifest(selected.type, manifest);
      if (result.valid) toast.success('Manifest is valid');
      else toast.error('Validation failed: ' + (result.errors?.join(', ') || 'Unknown'));
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setLoading(l => ({ ...l, validate: false }));
    }
  }, [selected, manifest]);

  const runArtifact = useCallback(async () => {
    if (!selected) return;
    setLoading(l => ({ ...l, run: true }));
    setRunOutput('');
    try {
      let input = {};
      try { input = JSON.parse(runInput); } catch { throw new Error('Invalid JSON input'); }
      const result = await api.runArtifact(selected.type, selected.name, input);
      if (result.requiresForm && result.form && result.formSessionId) {
        const initialData: Record<string, any> = {};
        result.form.fields.forEach(f => { if (f.defaultValue !== undefined) initialData[f.name] = f.defaultValue; });
        setFormModal({ open: true, form: result.form, sessionId: result.formSessionId, data: initialData });
      } else {
        // API may return { data } for tools/pipelines or { result, tools_used, reasoning } for agents
        // result.result can be a string or object - handle both
        let parsedResult = result.result;
        if (typeof parsedResult === 'string') {
          try { parsedResult = JSON.parse(parsedResult); } catch { /* keep as string */ }
        }

        const output: Record<string, any> = {};
        if (result.data !== undefined) {
          output.result = result.data;
        } else if (parsedResult !== undefined) {
          output.result = parsedResult;
        } else {
          output.result = result;
        }
        if (result.tools_used) output.tools_used = result.tools_used;
        if (result.reasoning) output.reasoning = result.reasoning;

        setRunOutput(JSON.stringify(output, null, 2));
      }
    } catch (e: any) {
      toast.error(e.message);
      setRunOutput(`Error: ${e.message}`);
    } finally {
      setLoading(l => ({ ...l, run: false }));
    }
  }, [selected, runInput]);

  const submitFormModal = useCallback(async () => {
    if (!formModal.sessionId) return;
    try {
      const result = await api.submitForm(formModal.sessionId, formModal.data);
      setRunOutput(JSON.stringify(result, null, 2));
      setFormModal({ open: false, data: {} });
      toast.success('Form submitted');
    } catch (e: any) {
      toast.error(e.message);
    }
  }, [formModal]);

  const loadExecutions = useCallback(async () => {
    try {
      const items = await api.fetchExecutions();
      setExecutions(items);
    } catch (e: any) {
      toast.error(e.message);
    }
  }, []);

  const loadExecutionDetail = useCallback(async (id: string) => {
    setSelectedExecution(id);
    setExecDetailTab('metadata');
    try {
      const [execution, log, input, output, stepsData] = await Promise.all([
        api.fetchExecutionDetail(id),
        api.fetchExecutionLog(id).catch(() => ''),
        api.fetchExecutionInput(id).catch(() => null),
        api.fetchExecutionOutput(id).catch(() => null),
        api.fetchExecutionSteps(id).catch(() => []),
      ]);
      setExecutionDetail({ execution, log, input, output, steps: stepsData });
    } catch (e: any) {
      toast.error(e.message);
    }
  }, []);

  const createArtifact = useCallback(async (type: ArtifactType, name: string) => {
    const templates: Record<ArtifactType, string> = {
      agent: `name: ${name}\ndescription: ""\nmodel:\n  provider: ollama\n  name: qwen2.5-coder:7b\ninstructions: |\n  You are a helpful agent.\ntools: []\n`,
      tool: `name: ${name}\ndescription: ""\ndriver: java\nclass: ""\nmethod: ""\nparameters: []\n`,
      pipeline: `name: ${name}\ndescription: ""\nsteps: []\n`,
      skill: `## Metadata\n\n\`\`\`json\n{\n  "name": "${name}",\n  "version": "1.0.0",\n  "description": "",\n  "tags": [],\n  "author": "",\n  "provider": "ollama",\n  "temperature": 0.7\n}\n\`\`\`\n\n## Model\n\n\`\`\`json\n{\n  "provider": "ollama",\n  "name": "qwen2.5-coder:7b",\n  "temperature": 0.7\n}\n\`\`\`\n\n## Instructions\n\nYou are a helpful skill. Describe your methodology here.\n`,
    };
    try {
      await api.saveManifest(type, name, templates[type]);
      toast.success(`${type} "${name}" created`);
      await loadRepo();
      const plural = type === 'skill' ? 'skills' : `${type}s` as keyof RepoModel;
      const created = repo[plural]?.find(a => a.name === name);
      if (created) selectArtifact(created);
    } catch (e: any) {
      toast.error(e.message);
    }
  }, [loadRepo, repo, selectArtifact]);

  return {
    repo, selected, manifest, setManifest, editorTab, setEditorTab,
    loading, apiOnline, sidebarOpen, setSidebarOpen,
    runInput, setRunInput, runOutput,
    formModal, setFormModal, submitFormModal,
    executions, selectedExecution, executionDetail, execDetailTab, setExecDetailTab,
    pipelineSteps, setPipelineSteps, pipelineInputParams, setPipelineInputParams, syncStepsToYaml,
    loadRepo, selectArtifact, saveManifest, validateManifest, runArtifact,
    loadExecutions, loadExecutionDetail, setSelectedExecution,
    createArtifact,
  };
}
