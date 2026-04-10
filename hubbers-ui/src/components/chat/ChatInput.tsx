import { useState } from 'react';
import { Send, ChevronDown, ChevronUp, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';

const EXAMPLES = [
  "Fetch RSS from TechCrunch and count items",
  "Check my Amazon shopping cart",
  "Read the latest Hacker News headlines",
  "What's in my downloads folder?",
];

interface ChatInputProps {
  onSend: (request: string, context?: object) => void;
  disabled: boolean;
  conversationId: string | null;
}

export function ChatInput({ onSend, disabled, conversationId }: ChatInputProps) {
  const [request, setRequest] = useState('');
  const [contextOpen, setContextOpen] = useState(false);
  const [contextJson, setContextJson] = useState('');

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

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="border-t bg-card p-4 space-y-3">
      {/* Example suggestions */}
      {!conversationId && (
        <div className="flex flex-wrap gap-2">
          {EXAMPLES.map(ex => (
            <button
              key={ex}
              onClick={() => setRequest(ex)}
              disabled={disabled}
              className="text-xs px-3 py-1.5 rounded-full border bg-background hover:bg-accent transition-colors disabled:opacity-50 flex items-center gap-1.5"
            >
              <Sparkles className="w-3 h-3 text-primary" />
              {ex}
            </button>
          ))}
        </div>
      )}

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

      {/* Main input */}
      <div className="flex gap-2 items-end">
        <div className="flex-1 relative">
          <Textarea
            value={request}
            onChange={e => setRequest(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={conversationId ? "Continue the conversation..." : "Describe what you want to do..."}
            className="min-h-[56px] max-h-[160px] resize-none pr-12"
            disabled={disabled}
          />
          <span className="absolute bottom-2 right-3 text-[10px] text-muted-foreground">
            {request.length}
          </span>
        </div>
        <Button
          onClick={handleSend}
          disabled={disabled || !request.trim()}
          size="icon"
          className="h-[56px] w-[56px] shrink-0"
        >
          <Send className="w-5 h-5" />
        </Button>
      </div>
    </div>
  );
}
