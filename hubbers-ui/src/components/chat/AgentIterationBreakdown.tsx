import { useState } from 'react';
import { ChevronDown, ChevronRight, CheckCircle2, Loader2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { AgentIterationTrace, ToolCallTrace } from '@/types/chat';

interface JsonPreviewProps {
  data: any;
  label: string;
}

function JsonPreview({ data, label }: JsonPreviewProps) {
  const [expanded, setExpanded] = useState(false);
  const MAX_PREVIEW_LENGTH = 500;
  
  if (!data) return null;
  
  const jsonString = JSON.stringify(data, null, 2);
  const isTruncated = jsonString.length > MAX_PREVIEW_LENGTH;
  const preview = isTruncated ? jsonString.slice(0, MAX_PREVIEW_LENGTH) + '...' : jsonString;

  return (
    <div className="mt-2">
      <p className="text-xs font-medium text-muted-foreground mb-1">{label}</p>
      <pre className="text-xs bg-muted p-2 rounded overflow-auto max-h-60 border">
        {expanded ? jsonString : preview}
      </pre>
      {isTruncated && (
        <Button 
          variant="ghost" 
          size="sm" 
          className="mt-1 h-6 text-xs"
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? 'Show Less' : 'View Full'}
        </Button>
      )}
    </div>
  );
}

interface ToolCallDisplayProps {
  toolCall: ToolCallTrace;
}

function ToolCallDisplay({ toolCall }: ToolCallDisplayProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="border rounded-lg p-2 bg-card">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between text-left"
      >
        <div className="flex items-center gap-2">
          {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
          <span className="text-xs font-mono font-semibold">{toolCall.toolName}</span>
          <Badge variant={toolCall.success ? "default" : "destructive"} className="text-[10px]">
            {toolCall.success ? 'Success' : 'Failed'}
          </Badge>
          <span className="text-[10px] text-muted-foreground">
            {toolCall.durationMs}ms
          </span>
        </div>
      </button>

      {expanded && (
        <div className="mt-2 space-y-2 border-t pt-2">
          {toolCall.input && <JsonPreview data={toolCall.input} label="Input" />}
          {toolCall.output && <JsonPreview data={toolCall.output} label="Output" />}
          {toolCall.error && (
            <div className="text-xs text-destructive p-2 bg-destructive/10 rounded">
              {toolCall.error}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

interface AgentIterationBreakdownProps {
  iterations: AgentIterationTrace[];
}

export function AgentIterationBreakdown({ iterations }: AgentIterationBreakdownProps) {
  const [expandedIterations, setExpandedIterations] = useState<Set<number>>(
    new Set(iterations.length > 0 ? [iterations.length] : [])
  );

  const toggleIteration = (iterationNumber: number) => {
    setExpandedIterations(prev => {
      const newSet = new Set(prev);
      if (newSet.has(iterationNumber)) {
        newSet.delete(iterationNumber);
      } else {
        newSet.add(iterationNumber);
      }
      return newSet;
    });
  };

  return (
    <div className="space-y-2">
      <p className="text-xs font-semibold text-muted-foreground mb-3">
        Agent Iterations ({iterations.length} total)
      </p>
      
      <div className="space-y-3">
        {iterations.map((iteration) => {
          const isExpanded = expandedIterations.has(iteration.iterationNumber);
          const hasToolCalls = iteration.toolCalls && iteration.toolCalls.length > 0;

          return (
            <div 
              key={iteration.iterationNumber}
              className="border rounded-lg p-3 bg-card"
            >
              <button
                onClick={() => toggleIteration(iteration.iterationNumber)}
                className="w-full flex items-center justify-between text-left"
              >
                <div className="flex items-center gap-2">
                  {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                  <div className="flex items-center gap-2">
                    {iteration.isComplete ? (
                      <CheckCircle2 className="w-4 h-4 text-green-500" />
                    ) : (
                      <Loader2 className="w-4 h-4 text-yellow-500" />
                    )}
                    <span className="text-sm font-semibold">
                      Iteration {iteration.iterationNumber}
                    </span>
                    {hasToolCalls && (
                      <Badge variant="secondary" className="text-[10px]">
                        {iteration.toolCalls.length} tool{iteration.toolCalls.length !== 1 ? 's' : ''}
                      </Badge>
                    )}
                    <span className="text-[10px] text-muted-foreground">
                      {iteration.durationMs}ms
                    </span>
                  </div>
                </div>
              </button>

              {isExpanded && (
                <div className="mt-3 space-y-3 border-t pt-3">
                  {/* Reasoning */}
                  {iteration.reasoning && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-1">Reasoning</p>
                      <div className="text-xs bg-muted p-2 rounded border whitespace-pre-wrap">
                        {iteration.reasoning}
                      </div>
                    </div>
                  )}

                  {/* Tool Calls */}
                  {hasToolCalls && (
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-2">Tool Calls</p>
                      <div className="space-y-2">
                        {iteration.toolCalls.map((toolCall, idx) => (
                          <ToolCallDisplay key={idx} toolCall={toolCall} />
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Result */}
                  {iteration.result && (
                    <JsonPreview data={iteration.result} label="Iteration Result" />
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
