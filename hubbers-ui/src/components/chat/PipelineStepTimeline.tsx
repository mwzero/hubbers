import { useState } from 'react';
import { CheckCircle2, XCircle, Clock, ChevronDown, ChevronUp } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import type { PipelineStepTrace } from '@/types/chat';

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

interface PipelineStepTimelineProps {
  steps: PipelineStepTrace[];
}

export function PipelineStepTimeline({ steps }: PipelineStepTimelineProps) {
  const [expandedSteps, setExpandedSteps] = useState<Set<number>>(new Set());

  const toggleStep = (stepNumber: number) => {
    setExpandedSteps(prev => {
      const newSet = new Set(prev);
      if (newSet.has(stepNumber)) {
        newSet.delete(stepNumber);
      } else {
        newSet.add(stepNumber);
      }
      return newSet;
    });
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return <CheckCircle2 className="w-4 h-4 text-green-500" />;
      case 'FAILED':
        return <XCircle className="w-4 h-4 text-red-500" />;
      default:
        return <Clock className="w-4 h-4 text-yellow-500" />;
    }
  };

  const getArtifactTypeColor = (type: string) => {
    switch (type) {
      case 'agent':
        return 'bg-blue-500/10 text-blue-700 border-blue-500/20';
      case 'tool':
        return 'bg-green-500/10 text-green-700 border-green-500/20';
      case 'pipeline':
        return 'bg-purple-500/10 text-purple-700 border-purple-500/20';
      case 'skill':
        return 'bg-orange-500/10 text-orange-700 border-orange-500/20';
      default:
        return 'bg-gray-500/10 text-gray-700 border-gray-500/20';
    }
  };

  return (
    <div className="space-y-2">
      <p className="text-xs font-semibold text-muted-foreground mb-3">
        Pipeline Execution ({steps.length} steps)
      </p>
      <div className="relative space-y-3">
        {/* Vertical timeline line */}
        <div className="absolute left-[11px] top-2 bottom-2 w-px bg-border" />
        
        {steps.map((step, idx) => {
          const isExpanded = expandedSteps.has(step.stepNumber);
          const isLast = idx === steps.length - 1;

          return (
            <div key={step.stepNumber} className="relative">
              {/* Timeline dot */}
              <div className="absolute left-0 top-2 w-6 h-6 rounded-full bg-background border-2 flex items-center justify-center z-10">
                {getStatusIcon(step.status)}
              </div>

              {/* Step card */}
              <div className="ml-10 p-3 rounded-lg border bg-card">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold">
                        Step {step.stepNumber}: {step.stepName}
                      </span>
                      <Badge 
                        variant="outline" 
                        className={`text-[10px] font-mono ${getArtifactTypeColor(step.artifactType)}`}
                      >
                        {step.artifactType}: {step.artifactName}
                      </Badge>
                      <span className="text-[10px] text-muted-foreground">
                        {step.durationMs}ms
                      </span>
                    </div>
                    
                    {step.error && (
                      <p className="text-xs text-destructive mt-1">{step.error}</p>
                    )}
                  </div>

                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-6 w-6 p-0"
                    onClick={() => toggleStep(step.stepNumber)}
                  >
                    {isExpanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                  </Button>
                </div>

                {isExpanded && (
                  <div className="mt-3 space-y-2 border-t pt-3">
                    {step.input && <JsonPreview data={step.input} label="Input" />}
                    {step.output && <JsonPreview data={step.output} label="Output" />}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
