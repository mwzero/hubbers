import { useState, useEffect } from 'react';
import { Database, Wrench, Workflow, BookOpen, ChevronRight, RefreshCw, Plus, Globe, Folder, FolderOpen, FileJson } from 'lucide-react';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { Artifact, ArtifactType, RepoModel } from '@/types/workspace';
import { fetchBrunoProjects, fetchBrunoRequests, type BrunoRequest } from '@/lib/api';

// ── Bruno tree ────────────────────────────────────────────────────────────
interface TreeNode {
  name: string;
  path: string;      // for folders: ancestor path; for requests: full path
  isFolder: boolean;
  children: TreeNode[];
}

function buildTree(requests: BrunoRequest[]): TreeNode[] {
  const roots: TreeNode[] = [];
  for (const req of requests) {
    const parts = req.path.split('/');
    let level = roots;
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const isLast = i === parts.length - 1;
      let node = level.find(n => n.name === part);
      if (!node) {
        node = {
          name: isLast ? req.name : part,
          path: parts.slice(0, i + 1).join('/'),
          isFolder: !isLast,
          children: [],
        };
        level.push(node);
      }
      level = node.children;
    }
  }
  return roots;
}

interface BrunoTreeNodesProps {
  nodes: TreeNode[];
  project: string;
  depth: number;
  selectedPath: string | null;
  onSelect: (req: BrunoRequest) => void;
}

function BrunoTreeNodes({ nodes, project, depth, selectedPath, onSelect }: BrunoTreeNodesProps) {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const toggle = (path: string) =>
    setExpanded(prev => { const s = new Set(prev); s.has(path) ? s.delete(path) : s.add(path); return s; });

  return (
    <div style={{ paddingLeft: depth === 0 ? 0 : 12 }}>
      {nodes.map(node => {
        if (node.isFolder) {
          const open = expanded.has(node.path);
          return (
            <div key={node.path}>
              <button
                onClick={() => toggle(node.path)}
                className="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-md text-[11px] text-muted-foreground hover:bg-muted transition-colors"
              >
                {open
                  ? <FolderOpen className="w-3 h-3 shrink-0 text-orange-400" />
                  : <Folder className="w-3 h-3 shrink-0 text-orange-400" />}
                <span className="truncate font-medium">{node.name}</span>
                <ChevronRight className={`w-3 h-3 shrink-0 ml-auto transition-transform text-muted-foreground/50 ${open ? 'rotate-90' : ''}`} />
              </button>
              {open && (
                <BrunoTreeNodes
                  nodes={node.children}
                  project={project}
                  depth={depth + 1}
                  selectedPath={selectedPath}
                  onSelect={onSelect}
                />
              )}
            </div>
          );
        }
        const isActive = selectedPath === node.path;
        return (
          <button
            key={node.path}
            onClick={() => onSelect({ path: node.path, name: node.name })}
            className={`w-full flex items-center gap-1.5 px-2 py-1.5 rounded-md text-[11px] transition-all ${
              isActive
                ? 'bg-orange-500/10 border-l-[3px] border-orange-500 text-orange-700 dark:text-orange-300 font-semibold'
                : 'text-muted-foreground hover:bg-muted border-l-[3px] border-transparent'
            }`}
            title={node.path}
          >
            <FileJson className={`w-3 h-3 shrink-0 ${isActive ? 'text-orange-500' : 'text-orange-400/70'}`} />
            <span className="truncate">{node.name}</span>
          </button>
        );
      })}
    </div>
  );
}

interface SidebarPanelProps {
  repo: RepoModel;
  selected: Artifact | null;
  loading: boolean;
  onSelect: (art: Artifact) => void;
  onRefresh: () => void;
  onCreate: (type: ArtifactType, name: string) => void;
  onSelectBrunoRequest?: (project: string, request: BrunoRequest) => void;
  selectedBrunoFile?: { project: string; path: string } | null;
}

const sections = [
  { key: 'agents' as const, label: 'Agents', type: 'agent' as ArtifactType, icon: Database, colorClass: 'text-agent' },
  { key: 'tools' as const, label: 'Tools', type: 'tool' as ArtifactType, icon: Wrench, colorClass: 'text-tool' },
  { key: 'pipelines' as const, label: 'Pipelines', type: 'pipeline' as ArtifactType, icon: Workflow, colorClass: 'text-pipeline' },
  { key: 'skills' as const, label: 'Skills', type: 'skill' as ArtifactType, icon: BookOpen, colorClass: 'text-skill' },
] as const;

export function SidebarPanel({ repo, selected, loading, onSelect, onRefresh, onCreate, onSelectBrunoRequest, selectedBrunoFile }: SidebarPanelProps) {
  const [newDialog, setNewDialog] = useState<{ open: boolean; type: ArtifactType; label: string }>({ open: false, type: 'agent', label: '' });
  const [newName, setNewName] = useState('');

  const [brunoProjects, setBrunoProjects] = useState<string[]>([]);
  const [brunoRequests, setBrunoRequests] = useState<Record<string, BrunoRequest[]>>({});

  useEffect(() => {
    fetchBrunoProjects().then(setBrunoProjects).catch(() => {});
  }, []);

  const handleBrunoProjectOpen = (project: string) => {
    if (brunoRequests[project] !== undefined) return;
    fetchBrunoRequests(project)
      .then(reqs => setBrunoRequests(prev => ({ ...prev, [project]: reqs })))
      .catch(() => setBrunoRequests(prev => ({ ...prev, [project]: [] })));
  };

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
                        <Badge variant="secondary" className="ml-auto text-[9px] px-1 py-0 h-3.5">
                          draft
                        </Badge>
                        <Badge variant="outline" className="text-[9px] px-1 py-0 h-3.5 opacity-0 group-hover:opacity-100 transition-opacity">
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

          {/* ── Bruno Collections ──────────────────────────── */}
          {brunoProjects.length > 0 && (
            <AccordionItem value="bruno" className="border-none">
              <AccordionTrigger className="flex-1 py-2 px-1 text-xs font-semibold hover:no-underline">
                <span className="flex items-center gap-2">
                  <Globe className="w-3.5 h-3.5 text-orange-500" />
                  Bruno
                  <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 font-normal">
                    {brunoProjects.length}
                  </Badge>
                </span>
              </AccordionTrigger>
              <AccordionContent className="pb-1">
                <Accordion
                  type="multiple"
                  className="pl-1"
                  onValueChange={vals => {
                    const justOpened = vals.find(v => brunoRequests[v] === undefined);
                    if (justOpened) handleBrunoProjectOpen(justOpened);
                  }}
                >
                  {brunoProjects.map(project => (
                    <AccordionItem key={project} value={project} className="border-none">
                      <AccordionTrigger className="py-1.5 px-1 text-xs hover:no-underline font-normal">
                        <span className="flex items-center gap-1.5 text-orange-600 dark:text-orange-400">
                          <Globe className="w-3 h-3 shrink-0" />
                          <span className="truncate">{project}</span>
                          {brunoRequests[project] && (
                            <Badge variant="secondary" className="text-[9px] px-1 py-0 h-3.5 font-normal ml-1">
                              {brunoRequests[project].length}
                            </Badge>
                          )}
                        </span>
                      </AccordionTrigger>
                      <AccordionContent className="pb-0 pt-0.5">
                        {brunoRequests[project] === undefined && (
                          <p className="text-[10px] text-muted-foreground px-3 py-1">Loading…</p>
                        )}
                        {brunoRequests[project]?.length === 0 && (
                          <p className="text-[10px] text-muted-foreground px-3 py-1">No requests found</p>
                        )}
                        {brunoRequests[project]?.length > 0 && (
                          <BrunoTreeNodes
                            nodes={buildTree(brunoRequests[project])}
                            project={project}
                            depth={0}
                            selectedPath={selectedBrunoFile?.project === project ? selectedBrunoFile.path : null}
                            onSelect={req => onSelectBrunoRequest?.(project, req)}
                          />
                        )}
                      </AccordionContent>
                    </AccordionItem>
                  ))}
                </Accordion>
              </AccordionContent>
            </AccordionItem>
          )}
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
