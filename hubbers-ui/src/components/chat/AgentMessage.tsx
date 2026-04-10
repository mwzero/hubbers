import { useState } from 'react';
import { CheckCircle2, XCircle, Copy, Download, ChevronDown, ChevronUp, RefreshCw, Bot } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import type { ChatMessage } from '@/types/chat';
import { toast } from '@/components/ui/sonner';

export function AgentMessage({ message }: { message: ChatMessage }) {
  const [reasoningOpen, setReasoningOpen] = useState(false);
  const [resultOpen, setResultOpen] = useState(true);

  if (message.isLoading) {
    return (
      <div className="flex gap-3 items-start">
        <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
          <Bot className="w-4 h-4 text-primary" />
        </div>
        <div className="bg-card border rounded-2xl rounded-tl-sm px-4 py-3 max-w-[85%] animate-pulse">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <RefreshCw className="w-4 h-4 animate-spin" />
            <span>Agent is thinking and calling tools...</span>
          </div>
        </div>
      </div>
    );
  }

  if (message.error) {
    return (
      <div className="flex gap-3 items-start">
        <div className="w-8 h-8 rounded-full bg-destructive/10 flex items-center justify-center shrink-0">
          <XCircle className="w-4 h-4 text-destructive" />
        </div>
        <div className="bg-card border border-destructive/30 rounded-2xl rounded-tl-sm px-4 py-3 max-w-[85%]">
          <p className="text-sm text-destructive">{message.error}</p>
        </div>
      </div>
    );
  }

  const isJson = message.result && typeof message.result === 'object';
  const resultStr = isJson ? JSON.stringify(message.result, null, 2) : String(message.result ?? message.content);

  const copyResult = () => {
    navigator.clipboard.writeText(resultStr);
    toast.success('Copied to clipboard');
  };

  const downloadResult = () => {
    const blob = new Blob([resultStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `result-${message.conversationId || 'output'}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex gap-3 items-start">
      <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
        <Bot className="w-4 h-4 text-primary" />
      </div>
      <div className="bg-card border rounded-2xl rounded-tl-sm px-4 py-3 max-w-[85%] space-y-3">
        {/* Status + meta */}
        <div className="flex items-center gap-2 flex-wrap">
          {message.status === 'SUCCESS' && <CheckCircle2 className="w-4 h-4 text-success" />}
          {message.status === 'FAILED' && <XCircle className="w-4 h-4 text-destructive" />}
          {message.status && (
            <Badge variant={message.status === 'SUCCESS' ? 'default' : 'destructive'} className="text-[10px]">
              {message.status}
            </Badge>
          )}
          {message.executionTime != null && (
            <span className="text-[10px] text-muted-foreground">{message.executionTime.toFixed(1)}s</span>
          )}
          {message.iterations != null && (
            <span className="text-[10px] text-muted-foreground">{message.iterations} iter</span>
          )}
        </div>

        {/* Result */}
        <Collapsible open={resultOpen} onOpenChange={setResultOpen}>
          <CollapsibleTrigger className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground">
            {resultOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            📋 Result
          </CollapsibleTrigger>
          <CollapsibleContent>
            <pre className="mt-2 p-3 rounded-lg bg-muted/50 text-xs font-mono whitespace-pre-wrap max-h-[300px] overflow-auto break-all">
              {resultStr}
            </pre>
            <div className="flex gap-1.5 mt-1.5">
              <Button variant="ghost" size="sm" className="h-6 text-[10px] gap-1" onClick={copyResult}>
                <Copy className="w-3 h-3" /> Copy
              </Button>
              {isJson && (
                <Button variant="ghost" size="sm" className="h-6 text-[10px] gap-1" onClick={downloadResult}>
                  <Download className="w-3 h-3" /> Download
                </Button>
              )}
            </div>
          </CollapsibleContent>
        </Collapsible>

        {/* Tools used */}
        {message.toolsUsed && message.toolsUsed.length > 0 && (
          <div className="space-y-1">
            <span className="text-xs font-medium text-muted-foreground">🔧 Tools Used</span>
            <div className="flex flex-wrap gap-1">
              {message.toolsUsed.map(t => (
                <Badge key={t} variant="secondary" className="text-[10px] font-mono">{t}</Badge>
              ))}
            </div>
          </div>
        )}

        {/* Reasoning */}
        {message.reasoning && (
          <Collapsible open={reasoningOpen} onOpenChange={setReasoningOpen}>
            <CollapsibleTrigger className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground">
              {reasoningOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
              💭 Reasoning
            </CollapsibleTrigger>
            <CollapsibleContent>
              <p className="mt-2 text-xs text-muted-foreground whitespace-pre-wrap leading-relaxed">
                {message.reasoning}
              </p>
            </CollapsibleContent>
          </Collapsible>
        )}

        {/* Conversation ID */}
        {message.conversationId && (
          <p className="text-[10px] text-muted-foreground/60 font-mono">
            conv: {message.conversationId}
          </p>
        )}
      </div>
    </div>
  );
}
