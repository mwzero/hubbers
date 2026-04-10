import { MessageSquare, Trash2, ArrowLeft, Link2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { ChatInput } from '@/components/chat/ChatInput';
import { UserMessage } from '@/components/chat/UserMessage';
import { AgentMessage } from '@/components/chat/AgentMessage';
import { useChat } from '@/hooks/useChat';
import { Link } from 'react-router-dom';

export default function Chat() {
  const chat = useChat();

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="h-14 border-b flex items-center justify-between px-4 bg-card shrink-0">
        <div className="flex items-center gap-3">
          <Link to="/">
            <Button variant="ghost" size="icon" className="h-8 w-8">
              <ArrowLeft className="w-4 h-4" />
            </Button>
          </Link>
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
            <MessageSquare className="w-4 h-4 text-primary-foreground" />
          </div>
          <div>
            <h1 className="text-sm font-semibold leading-tight">Task Agent Chat</h1>
            {chat.systemInfo && (
              <p className="text-[10px] text-muted-foreground">
                {chat.systemInfo.agentName} · {chat.systemInfo.availableTools} tools
              </p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {chat.conversationId && (
            <Badge variant="secondary" className="text-[10px] font-mono gap-1">
              <Link2 className="w-3 h-3" />
              {chat.conversationId.slice(0, 8)}…
            </Badge>
          )}
          {chat.messages.length > 0 && (
            <Button variant="ghost" size="sm" className="h-7 text-xs gap-1" onClick={chat.clearConversation}>
              <Trash2 className="w-3 h-3" />
              New
            </Button>
          )}
        </div>
      </header>

      {/* Messages */}
      <ScrollArea className="flex-1" ref={chat.scrollRef}>
        <div className="max-w-3xl mx-auto p-4 space-y-4">
          {chat.messages.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 text-center space-y-3">
              <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center">
                <MessageSquare className="w-8 h-8 text-primary" />
              </div>
              <h2 className="text-lg font-semibold">Start a conversation</h2>
              <p className="text-sm text-muted-foreground max-w-sm">
                Describe a task in natural language and the agent will execute it using available tools.
              </p>
            </div>
          )}
          {chat.messages.map(msg =>
            msg.role === 'user'
              ? <UserMessage key={msg.id} message={msg} />
              : <AgentMessage key={msg.id} message={msg} />
          )}
        </div>
      </ScrollArea>

      {/* Conversation indicator */}
      {chat.conversationId && (
        <div className="text-center py-1 bg-primary/5 border-t border-primary/10">
          <span className="text-[10px] text-primary font-medium">
            Continuing conversation · {chat.conversationId.slice(0, 8)}…
          </span>
        </div>
      )}

      {/* Input */}
      <ChatInput
        onSend={chat.sendMessage}
        disabled={chat.isExecuting}
        conversationId={chat.conversationId}
      />
    </div>
  );
}
