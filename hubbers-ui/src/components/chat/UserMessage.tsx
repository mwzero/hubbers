import { User } from 'lucide-react';
import type { ChatMessage } from '@/types/chat';

export function UserMessage({ message }: { message: ChatMessage }) {
  return (
    <div className="flex gap-3 items-start justify-end">
      <div className="bg-primary text-primary-foreground rounded-2xl rounded-tr-sm px-4 py-3 max-w-[85%]">
        <p className="text-sm whitespace-pre-wrap">{message.content}</p>
      </div>
      <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center shrink-0 mt-0.5">
        <User className="w-4 h-4 text-primary" />
      </div>
    </div>
  );
}
