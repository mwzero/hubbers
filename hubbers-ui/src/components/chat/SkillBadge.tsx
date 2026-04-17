import { Brain, Code, Workflow } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import type { SkillInvocationTrace } from '@/types/chat';

interface SkillBadgeProps {
  skill: SkillInvocationTrace;
}

export function SkillBadge({ skill }: SkillBadgeProps) {
  const getModeIcon = (mode: string) => {
    switch (mode) {
      case 'llm-prompt':
        return <Brain className="w-3 h-3" />;
      case 'script':
        return <Code className="w-3 h-3" />;
      case 'hybrid':
        return <Workflow className="w-3 h-3" />;
      default:
        return null;
    }
  };

  const getModeColor = (mode: string) => {
    switch (mode) {
      case 'llm-prompt':
        return 'bg-blue-500/10 text-blue-700 border-blue-500/20';
      case 'script':
        return 'bg-green-500/10 text-green-700 border-green-500/20';
      case 'hybrid':
        return 'bg-purple-500/10 text-purple-700 border-purple-500/20';
      default:
        return 'bg-gray-500/10 text-gray-700 border-gray-500/20';
    }
  };

  return (
    <Badge 
      variant="outline"
      className={`text-[10px] font-mono gap-1 ${getModeColor(skill.executionMode)}`}
      title={`${skill.skillName} (${skill.executionMode}, ${skill.durationMs}ms)`}
    >
      {getModeIcon(skill.executionMode)}
      {skill.skillName}
      {!skill.success && <span className="text-red-600">✗</span>}
    </Badge>
  );
}
