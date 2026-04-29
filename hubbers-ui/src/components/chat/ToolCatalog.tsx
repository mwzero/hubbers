import { useState, useEffect } from 'react';
import { Wrench, Sparkles, Search } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Input } from '@/components/ui/input';
import { getTools, getSkills } from '@/lib/taskApi';

export function ToolCatalog() {
  const [tools, setTools] = useState<string[]>([]);
  const [skills, setSkills] = useState<string[]>([]);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    getTools().then(setTools).catch(() => {});
    getSkills().then(setSkills).catch(() => {});
  }, []);

  const filteredTools = filter
    ? tools.filter(t => t.toLowerCase().includes(filter.toLowerCase()))
    : tools;

  const filteredSkills = filter
    ? skills.filter(s => s.toLowerCase().includes(filter.toLowerCase()))
    : skills;

  return (
    <div className="flex flex-col h-full">
      <div className="p-3 border-b space-y-2">
        <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
          Catalog
        </h2>
        <div className="relative">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-3 h-3 text-muted-foreground" />
          <Input
            value={filter}
            onChange={e => setFilter(e.target.value)}
            placeholder="Filter..."
            className="h-7 text-xs pl-7"
          />
        </div>
      </div>
      <ScrollArea className="flex-1">
        <div className="p-2 space-y-3">
          {/* Tools section */}
          <div>
            <div className="flex items-center gap-1.5 px-2 py-1">
              <Wrench className="w-3 h-3 text-blue-500" />
              <span className="text-[10px] font-semibold text-muted-foreground uppercase">
                Tools ({filteredTools.length})
              </span>
            </div>
            <div className="space-y-0.5">
              {filteredTools.map(tool => (
                <div
                  key={tool}
                  className="px-2.5 py-1.5 rounded text-xs font-mono text-foreground/80 hover:bg-accent/50 transition-colors cursor-default"
                  title={tool}
                >
                  {tool}
                </div>
              ))}
              {filteredTools.length === 0 && (
                <p className="text-[10px] text-muted-foreground px-2 py-1">No tools found</p>
              )}
            </div>
          </div>

          {/* Skills section */}
          <div>
            <div className="flex items-center gap-1.5 px-2 py-1">
              <Sparkles className="w-3 h-3 text-purple-500" />
              <span className="text-[10px] font-semibold text-muted-foreground uppercase">
                Skills ({filteredSkills.length})
              </span>
            </div>
            <div className="space-y-0.5">
              {filteredSkills.map(skill => (
                <div
                  key={skill}
                  className="px-2.5 py-1.5 rounded text-xs font-mono text-foreground/80 hover:bg-accent/50 transition-colors cursor-default flex items-center gap-1.5"
                  title={skill}
                >
                  <Badge variant="outline" className="text-[8px] px-1 py-0 text-purple-500 border-purple-300">
                    skill
                  </Badge>
                  {skill}
                </div>
              ))}
              {filteredSkills.length === 0 && (
                <p className="text-[10px] text-muted-foreground px-2 py-1">No skills found</p>
              )}
            </div>
          </div>
        </div>
      </ScrollArea>
    </div>
  );
}
