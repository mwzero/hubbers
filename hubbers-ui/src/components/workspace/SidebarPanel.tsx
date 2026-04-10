import { useState } from 'react';
import { Database, Wrench, Workflow, BookOpen, ChevronRight, RefreshCw, Plus } from 'lucide-react';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { Artifact, ArtifactType, RepoModel } from '@/types/workspace';

interface SidebarPanelProps {
  repo: RepoModel;
  selected: Artifact | null;
  loading: boolean;
  onSelect: (art: Artifact) => void;
  onRefresh: () => void;
  onCreate: (type: ArtifactType, name: string) => void;
}

const sections = [
  { key: 'agents' as const, label: 'Agents', type: 'agent' as ArtifactType, icon: Database, colorClass: 'text-agent' },
  { key: 'tools' as const, label: 'Tools', type: 'tool' as ArtifactType, icon: Wrench, colorClass: 'text-tool' },
  { key: 'pipelines' as const, label: 'Pipelines', type: 'pipeline' as ArtifactType, icon: Workflow, colorClass: 'text-pipeline' },
  { key: 'skills' as const, label: 'Skills', type: 'skill' as ArtifactType, icon: BookOpen, colorClass: 'text-skill' },
] as const;

export function SidebarPanel({ repo, selected, loading, onSelect, onRefresh, onCreate }: SidebarPanelProps) {
  const [newDialog, setNewDialog] = useState<{ open: boolean; type: ArtifactType; label: string }>({ open: false, type: 'agent', label: '' });
  const [newName, setNewName] = useState('');

  const openNewDialog = (type: ArtifactType, label: string) => {
    setNewDialog({ open: true, type, label });
    setNewName('');
  };

  const handleCreate = () => {
    const trimmed = newName.trim();
    if (!trimmed) return;
    onCreate(newDialog.type, trimmed);
    setNewDialog(d => ({ ...d, open: false }));
  };

  return (
    <div className="h-full flex flex-col bg-card">
      <div className="px-3 py-3 flex items-center justify-between border-b">
        <span className="text-xs font-bold tracking-widest text-muted-foreground uppercase">Repository</span>
        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={onRefresh} disabled={loading}>
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
        </Button>
      </div>
      <ScrollArea className="flex-1">
        <Accordion type="multiple" defaultValue={['agents', 'tools', 'pipelines', 'skills']} className="px-2 py-1">
          {sections.map(({ key, label, type, icon: Icon, colorClass }) => (
            <AccordionItem key={key} value={key} className="border-none">
              <div className="flex items-center group">
                <AccordionTrigger className="flex-1 py-2 px-1 text-xs font-semibold hover:no-underline">
                  <span className="flex items-center gap-2">
                    <Icon className={`w-3.5 h-3.5 ${colorClass}`} />
                    {label}
                    <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 font-normal">
                      {repo[key].length}
                    </Badge>
                  </span>
                </AccordionTrigger>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-5 w-5 shrink-0 mr-1 opacity-0 group-hover:opacity-100 hover:opacity-100 focus:opacity-100 transition-opacity"
                  style={{ opacity: undefined }}
                  onClick={e => { e.stopPropagation(); openNewDialog(type, label); }}
                  title={`New ${label.slice(0, -1)}`}
                >
                  <Plus className={`w-3 h-3 ${colorClass}`} />
                </Button>
              </div>
              <AccordionContent className="pb-1">
                <div className="space-y-0.5">
                  {repo[key].map(art => {
                    const isActive = selected?.name === art.name && selected?.type === art.type;
                    return (
                      <button
                        key={art.name}
                        onClick={() => onSelect(art)}
                        className={`w-full flex items-center gap-2 px-2 py-1.5 rounded-md text-xs transition-all group ${
                          isActive
                            ? 'bg-primary/10 border-l-[3px] border-primary text-primary font-semibold shadow-sm'
                            : 'hover:bg-muted border-l-[3px] border-transparent'
                        }`}
                      >
                        <ChevronRight className={`w-3 h-3 shrink-0 transition-transform ${isActive ? 'rotate-90' : 'group-hover:translate-x-0.5'}`} />
                        <span className="truncate">{art.label}</span>
                        <Badge variant="outline" className="ml-auto text-[9px] px-1 py-0 h-3.5 opacity-0 group-hover:opacity-100 transition-opacity">
                          {art.type === 'skill' ? '.md' : '.yaml'}
                        </Badge>
                      </button>
                    );
                  })}
                  {repo[key].length === 0 && (
                    <p className="text-[10px] text-muted-foreground px-2 py-2">No {label.toLowerCase()} found</p>
                  )}
                </div>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </ScrollArea>

      <Dialog open={newDialog.open} onOpenChange={open => setNewDialog(d => ({ ...d, open }))}>
        <DialogContent className="sm:max-w-[400px]">
          <DialogHeader>
            <DialogTitle className="text-sm">New {newDialog.label.slice(0, -1)}</DialogTitle>
          </DialogHeader>
          <div className="py-2">
            <Label htmlFor="artifact-name" className="text-xs text-muted-foreground">Name</Label>
            <Input
              id="artifact-name"
              value={newName}
              onChange={e => setNewName(e.target.value)}
              placeholder={`my-${newDialog.type}`}
              className="mt-1.5 h-8 text-sm"
              onKeyDown={e => e.key === 'Enter' && handleCreate()}
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setNewDialog(d => ({ ...d, open: false }))}>Cancel</Button>
            <Button size="sm" onClick={handleCreate} disabled={!newName.trim()}>Create</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
