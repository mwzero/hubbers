import { MessageSquare, Trash2, ArrowLeft, Link2, Sparkles, Settings } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { ChatInput } from '@/components/chat/ChatInput';
import { UserMessage } from '@/components/chat/UserMessage';
import { AgentMessage } from '@/components/chat/AgentMessage';
import { useChat } from '@/hooks/useChat';
import { Link } from 'react-router-dom';

const EXAMPLES = [
  "Fetch RSS from TechCrunch and count items",
  "Check my Amazon shopping cart",
  "Read the latest Hacker News headlines",
  "What's in my downloads folder?",
];

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
            <h1 className="text-sm font-semibold leading-tight">
              {chat.selectedAgent || 'Task Agent Chat'}
            </h1>
            {chat.systemInfo && (
              <p className="text-[10px] text-muted-foreground">
                {chat.systemInfo.availableTools} tools available
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
          <Link to="/settings">
            <Button variant="ghost" size="icon" className="h-8 w-8">
              <Settings className="w-4 h-4" />
            </Button>
          </Link>
        </div>
      </header>

      {/* Messages */}
      <ScrollArea className="flex-1" ref={chat.scrollRef}>
        <div className="max-w-3xl mx-auto p-4 space-y-4">
          {chat.messages.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 text-center space-y-6">
              <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center">
                <MessageSquare className="w-8 h-8 text-primary" />
              </div>
              <div className="space-y-3">
                <h2 className="text-lg font-semibold">Start a conversation</h2>
                <p className="text-sm text-muted-foreground max-w-sm">
                  Type <code className="px-1.5 py-0.5 rounded bg-muted font-mono text-xs">/</code> to select an agent, then describe your task.
                </p>
              </div>
              {/* Prompt suggestions */}
              <div className="w-full max-w-md space-y-2">
                <p className="text-xs font-medium text-muted-foreground">Try these examples:</p>
                <div className="grid grid-cols-1 gap-2">
                  {EXAMPLES.map(ex => (
                    <button
                      key={ex}
                      onClick={() => {
                        const input = document.querySelector('textarea') as HTMLTextAreaElement;
                        if (input) {
                          input.value = ex;
                          input.dispatchEvent(new Event('input', { bubbles: true }));
                          input.focus();
                        }
                      }}
                      disabled={chat.isExecuting}
                      className="text-left text-sm px-4 py-3 rounded-lg border bg-card hover:bg-accent transition-colors disabled:opacity-50 flex items-start gap-2"
                    >
                      <Sparkles className="w-4 h-4 text-primary shrink-0 mt-0.5" />
                      <span>{ex}</span>
                    </button>
                  ))}
                </div>
              </div>
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
        selectedAgent={chat.selectedAgent}
        availableAgents={chat.availableAgents}
        onAgentSelect={chat.setSelectedAgent}
      />
    </div>
  );
}
