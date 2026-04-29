import { useEffect, useState, useMemo } from 'react';
import { Sparkles, Cpu, FileText } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { fetchModelProviders } from '@/lib/api';
import type { ModelProviderInfo } from '@/types/workspace';

interface SkillModel {
  metadata: {
    name: string;
    description: string;
    executionMode: 'llm-prompt' | 'hybrid' | 'script';
    author: string;
    version: string;
    tags: string[];
  };
  model: {
    provider: string;
    name: string;
    temperature: number;
  };
  instructions: string;
}

function emptySkill(): SkillModel {
  return {
    metadata: {
      name: '', description: '', executionMode: 'llm-prompt',
      author: '', version: '1.0', tags: [],
    },
    model: { provider: 'ollama', name: 'qwen2.5-coder:7b', temperature: 0.3 },
    instructions: '# Skill Instructions\n\n## When to use this skill\n\nUse this skill when...\n\n## How to execute\n\n1. Step one\n2. Step two\n\n## Output format\n\nReturn a JSON object with:\n```json\n{\n  "result": "..."\n}\n```\n',
  };
}

function parseSkillMd(md: string): SkillModel | null {
  try {
    const skill = emptySkill();
    const sections = md.split(/^## /m).filter(Boolean);

    for (const section of sections) {
      const jsonMatch = section.match(/```json\s*\n([\s\S]*?)```/);
      if (section.startsWith('Metadata') && jsonMatch) {
        try {
          const meta = JSON.parse(jsonMatch[1]);
          skill.metadata = { ...skill.metadata, ...meta };
          if (!skill.metadata.tags) skill.metadata.tags = [];
        } catch { /* keep defaults */ }
      } else if (section.startsWith('Model') && jsonMatch) {
        try {
          const model = JSON.parse(jsonMatch[1]);
          skill.model = { ...skill.model, ...model };
        } catch { /* keep defaults */ }
      } else if (section.startsWith('Instructions')) {
        skill.instructions = section.replace(/^Instructions\s*\n/, '').trim();
      }
    }
    return skill;
  } catch {
    return null;
  }
}

function skillToMd(skill: SkillModel): string {
  const metaJson = JSON.stringify({
    name: skill.metadata.name,
    description: skill.metadata.description,
    executionMode: skill.metadata.executionMode,
    author: skill.metadata.author,
    version: skill.metadata.version,
    ...(skill.metadata.tags.length > 0 ? { tags: skill.metadata.tags } : {}),
  }, null, 2);

  const modelJson = JSON.stringify(skill.model, null, 2);

  return `## Metadata

\`\`\`json
${metaJson}
\`\`\`

## Model

\`\`\`json
${modelJson}
\`\`\`

## Instructions

${skill.instructions}
`;
}

interface SkillBuilderProps {
  manifest: string;
  onManifestChange: (md: string) => void;
}

export function SkillBuilder({ manifest, onManifestChange }: SkillBuilderProps) {
  const [skill, setSkill] = useState<SkillModel>(() => parseSkillMd(manifest) || emptySkill());
  const [tagInput, setTagInput] = useState('');
  const [providers, setProviders] = useState<ModelProviderInfo[]>([]);

  useEffect(() => {
    fetchModelProviders().then(setProviders).catch(() => setProviders([]));
  }, []);

  const md = useMemo(() => skillToMd(skill), [skill]);
  const syncToMarkdown = () => onManifestChange(md);

  const updateMeta = (patch: Partial<SkillModel['metadata']>) => setSkill(prev => ({ ...prev, metadata: { ...prev.metadata, ...patch } }));
  const updateModel = (patch: Partial<SkillModel['model']>) => setSkill(prev => ({ ...prev, model: { ...prev.model, ...patch } }));

  const addTag = () => {
    const tag = tagInput.trim();
    if (tag && !skill.metadata.tags.includes(tag)) {
      updateMeta({ tags: [...skill.metadata.tags, tag] });
    }
    setTagInput('');
  };

  const removeTag = (tag: string) => {
    updateMeta({ tags: skill.metadata.tags.filter(t => t !== tag) });
  };

  return (
    <ScrollArea className="h-full">
      <div className="p-4 space-y-4">
        {/* Sync bar */}
        <div className="flex items-center justify-between">
          <p className="text-[10px] text-muted-foreground">Visual editor — changes sync to Markdown on click</p>
          <Button size="sm" className="h-7 text-xs gap-1" onClick={syncToMarkdown}>
            Sync to Markdown
          </Button>
        </div>

        {/* Metadata */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Sparkles className="w-3 h-3" /> Skill Metadata
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <div className="grid grid-cols-[1fr_80px] gap-2">
              <div>
                <Label className="text-[10px]">Name</Label>
                <Input value={skill.metadata.name} onChange={e => updateMeta({ name: e.target.value })} placeholder="my-skill" className="h-7 text-xs font-mono" />
              </div>
              <div>
                <Label className="text-[10px]">Version</Label>
                <Input value={skill.metadata.version} onChange={e => updateMeta({ version: e.target.value })} placeholder="1.0" className="h-7 text-xs" />
              </div>
            </div>
            <div>
              <Label className="text-[10px]">Description</Label>
              <Textarea value={skill.metadata.description} onChange={e => updateMeta({ description: e.target.value })} placeholder="What does this skill do? When should it be used?" className="min-h-[60px] text-xs resize-y" />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <Label className="text-[10px]">Execution Mode</Label>
                <Select value={skill.metadata.executionMode} onValueChange={v => updateMeta({ executionMode: v as SkillModel['metadata']['executionMode'] })}>
                  <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="llm-prompt">LLM Prompt</SelectItem>
                    <SelectItem value="hybrid">Hybrid</SelectItem>
                    <SelectItem value="script">Script</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="text-[10px]">Author</Label>
                <Input value={skill.metadata.author} onChange={e => updateMeta({ author: e.target.value })} placeholder="author" className="h-7 text-xs" />
              </div>
            </div>
            <div>
              <Label className="text-[10px]">Tags</Label>
              <div className="flex gap-1.5 flex-wrap mb-1.5">
                {skill.metadata.tags.map(tag => (
                  <Badge key={tag} variant="secondary" className="text-[10px] cursor-pointer gap-0.5" onClick={() => removeTag(tag)}>
                    {tag} ×
                  </Badge>
                ))}
              </div>
              <div className="flex gap-1">
                <Input
                  value={tagInput}
                  onChange={e => setTagInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
                  placeholder="Add tag..."
                  className="h-7 text-xs flex-1"
                />
                <Button variant="outline" size="sm" className="h-7 text-xs" onClick={addTag}>Add</Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Model */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <Cpu className="w-3 h-3" /> Model
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-2">
            <div className="grid grid-cols-3 gap-2">
              <div>
                <Label className="text-[10px]">Provider</Label>
                <Select value={skill.model.provider} onValueChange={v => updateModel({ provider: v })}>
                  <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {(providers.length ? providers : [
                      { id: 'ollama', label: 'Ollama', local: true, configured: true },
                      { id: 'llama-cpp', label: 'llama.cpp', local: true, configured: false },
                      { id: 'openai', label: 'OpenAI', local: false, configured: false },
                      { id: 'anthropic', label: 'Anthropic', local: false, configured: false },
                    ]).map(provider => (
                      <SelectItem key={provider.id} value={provider.id}>
                        {provider.label}{provider.local ? ' (Local)' : ''}{provider.configured ? '' : ' - not configured'}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="text-[10px]">Model Name</Label>
                <Input value={skill.model.name} onChange={e => updateModel({ name: e.target.value })} placeholder="qwen2.5-coder:7b" className="h-7 text-xs font-mono" />
              </div>
              <div>
                <Label className="text-[10px]">Temperature</Label>
                <Input type="number" value={skill.model.temperature} onChange={e => updateModel({ temperature: parseFloat(e.target.value) || 0 })} className="h-7 text-xs" min={0} max={2} step={0.1} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Instructions */}
        <Card>
          <CardHeader className="py-2 px-3">
            <CardTitle className="text-[10px] font-bold tracking-widest text-muted-foreground uppercase flex items-center gap-1.5">
              <FileText className="w-3 h-3" /> Instructions (Markdown)
            </CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3">
            <p className="text-[9px] text-muted-foreground mb-1.5">
              Write instructions in Markdown. Include: when to use, how to execute, output format, and examples.
            </p>
            <Textarea
              value={skill.instructions}
              onChange={e => setSkill(prev => ({ ...prev, instructions: e.target.value }))}
              placeholder="# Skill Instructions..."
              className="min-h-[300px] font-mono text-xs resize-y"
            />
          </CardContent>
        </Card>
      </div>
    </ScrollArea>
  );
}
