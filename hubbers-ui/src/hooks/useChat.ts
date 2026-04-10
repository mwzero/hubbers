import { useState, useCallback, useRef, useEffect } from 'react';
import type { ChatMessage, SystemInfo } from '@/types/chat';
import { executeTask, continueTask, getSystemInfo } from '@/lib/taskApi';
import { toast } from '@/components/ui/sonner';

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getSystemInfo().then(setSystemInfo).catch(() => {});
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const sendMessage = useCallback(async (request: string, context?: object) => {
    if (!request.trim() || isExecuting) return;

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: request,
      timestamp: new Date(),
    };

    const loadingMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'agent',
      content: '',
      timestamp: new Date(),
      isLoading: true,
    };

    setMessages(prev => [...prev, userMsg, loadingMsg]);
    setIsExecuting(true);

    const startTime = Date.now();

    try {
      const result = conversationId
        ? await continueTask(request, conversationId, context)
        : await executeTask(request, context);

      const elapsed = (Date.now() - startTime) / 1000;

      if (!conversationId && result.conversationId) {
        setConversationId(result.conversationId);
      }

      const agentMsg: ChatMessage = {
        id: loadingMsg.id,
        role: 'agent',
        content: typeof result.result === 'string' ? result.result : JSON.stringify(result.result, null, 2),
        timestamp: new Date(),
        result: result.result,
        reasoning: result.reasoning,
        toolsUsed: result.toolsUsed,
        iterations: result.iterations,
        status: result.status,
        conversationId: result.conversationId,
        executionTime: elapsed,
      };

      setMessages(prev => prev.map(m => m.id === loadingMsg.id ? agentMsg : m));
    } catch (err: any) {
      const errorMsg: ChatMessage = {
        id: loadingMsg.id,
        role: 'agent',
        content: '',
        timestamp: new Date(),
        error: err.message || 'Failed to execute task',
        status: 'ERROR',
      };
      setMessages(prev => prev.map(m => m.id === loadingMsg.id ? errorMsg : m));
      toast.error(err.message || 'Failed to execute task');
    } finally {
      setIsExecuting(false);
    }
  }, [conversationId, isExecuting]);

  const clearConversation = useCallback(() => {
    setMessages([]);
    setConversationId(null);
  }, []);

  return {
    messages,
    conversationId,
    isExecuting,
    systemInfo,
    scrollRef,
    sendMessage,
    clearConversation,
  };
}
