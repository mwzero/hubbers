import { useState, useRef, useEffect } from 'react';
import { Send, ChevronDown, ChevronUp, Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';

interface ChatInputProps {
  onSend: (request: string, context?: object) => void;
  disabled: boolean;
  conversationId: string | null;
  selectedAgent: string;
  availableAgents: string[];
  onAgentSelect: (agent: string) => void;
}

export function ChatInput({ onSend, disabled, conversationId, selectedAgent, availableAgents, onAgentSelect }: ChatInputProps) {
  const [request, setRequest] = useState('');
  const [contextOpen, setContextOpen] = useState(false);
  const [contextJson, setContextJson] = useState('');
  const [showAgentMenu, setShowAgentMenu] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Detect slash command at the start
  useEffect(() => {
    if (request === '/') {
      setShowAgentMenu(true);
      setSelectedIndex(0);
    } else if (request.startsWith('/') && !request.includes(' ')) {
      setShowAgentMenu(true);
    } else {
      setShowAgentMenu(false);
    }
  }, [request]);

  const filteredAgents = request.startsWith('/') && request.length > 1
    ? availableAgents.filter(agent => agent.toLowerCase().includes(request.slice(1).toLowerCase()))
    : availableAgents;

  const handleSend = () => {
    if (!request.trim()) return;
    let ctx: object | undefined;
    if (contextJson.trim()) {
      try { ctx = JSON.parse(contextJson); } catch {
        return;
      }
    }
    onSend(request, ctx);
    setRequest('');
  };

  const selectAgent = (agent: string) => {
    onAgentSelect(agent);
    setRequest('');
    setShowAgentMenu(false);
    textareaRef.current?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (showAgentMenu) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex(prev => (prev + 1) % filteredAgents.length);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex(prev => (prev - 1 + filteredAgents.length) % filteredAgents.length);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (filteredAgents.length > 0) {
          selectAgent(filteredAgents[selectedIndex]);
        }
      } else if (e.key === 'Escape') {
        e.preventDefault();
        setShowAgentMenu(false);
        setRequest('');
      }
      return;
    }

    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="border-t bg-card px-4 pb-4 space-y-3">
      {/* Agent badge */}
      <div className="pt-3 flex items-center gap-2">
        <span className="text-xs text-muted-foreground">Agent:</span>
        <Badge variant="secondary" className="text-xs font-mono">
          {selectedAgent}
        </Badge>
        <span className="text-[10px] text-muted-foreground italic">
          Type <code className="px-1 py-0.5 rounded bg-muted font-mono">/</code> to change
        </span>
      </div>

      {/* Context JSON collapsible */}
      <Collapsible open={contextOpen} onOpenChange={setContextOpen}>
        <CollapsibleTrigger className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors">
          {contextOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
          Advanced: Context JSON
        </CollapsibleTrigger>
        <CollapsibleContent>
          <Textarea
            value={contextJson}
            onChange={e => setContextJson(e.target.value)}
            placeholder='{"key": "value"}'
            className="mt-2 h-20 font-mono text-xs resize-none"
            disabled={disabled}
          />
        </CollapsibleContent>
      </Collapsible>

      {/* Main input with agent menu */}
      <div className="flex gap-2 items-end">
        <div className="flex-1 relative">
          {/* Agent selection menu */}
          {showAgentMenu && filteredAgents.length > 0 && (
            <div className="absolute bottom-full left-0 right-0 mb-2 bg-popover border rounded-lg shadow-lg overflow-hidden z-50">
              <div className="p-2 space-y-1 max-h-60 overflow-y-auto">
                {filteredAgents.map((agent, index) => (
                  <button
                    key={agent}
                    onClick={() => selectAgent(agent)}
                    className={`w-full text-left px-3 py-2 rounded text-sm transition-colors flex items-center justify-between ${
                      index === selectedIndex
                        ? 'bg-accent text-accent-foreground'
                        : 'hover:bg-accent/50'
                    }`}
                  >
                    <span className="font-mono">{agent}</span>
                    {agent === selectedAgent && <Check className="w-4 h-4 text-primary" />}
                  </button>
                ))}
              </div>
              <div className="px-3 py-2 bg-muted/50 border-t text-[10px] text-muted-foreground">
                Use ↑↓ to navigate, Enter to select, Esc to cancel
              </div>
            </div>
          )}
          
          <Textarea
            ref={textareaRef}
            value={request}
            onChange={e => setRequest(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={conversationId ? "Continue the conversation..." : "Type / to select agent, then describe your task..."}
            className="min-h-[56px] max-h-[160px] resize-none pr-12"
            disabled={disabled}
          />
          <span className="absolute bottom-2 right-3 text-[10px] text-muted-foreground">
            {request.length}
          </span>
        </div>
        <Button
          onClick={handleSend}
          disabled={disabled || !request.trim() || showAgentMenu}
          size="icon"
          className="h-[56px] w-[56px] shrink-0"
        >
          <Send className="w-5 h-5" />
        </Button>
      </div>
    </div>
  );
}
