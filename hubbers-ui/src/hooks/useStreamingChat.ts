import { useState, useCallback, useRef, useEffect } from 'react';
import type { ChatMessage, SystemInfo, ModelInfo, ConversationSummary } from '@/types/chat';
import { streamTask, getSystemInfo, getAgents, getModels, getConversations, getConversationMessages, deleteConversation } from '@/lib/taskApi';
import { toast } from '@/components/ui/sonner';

export function useStreamingChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [selectedAgent, setSelectedAgentState] = useState<string>('universal.task');
  const [availableAgents, setAvailableAgents] = useState<string[]>([]);
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>('');
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    getSystemInfo().then(setSystemInfo).catch(() => {});
    getAgents().then(setAvailableAgents).catch(() => {});
    getModels().then(setModels).catch(() => {});
    getConversations().then(setConversations).catch(() => {});
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const refreshConversations = useCallback(() => {
    getConversations().then(setConversations).catch(() => {});
  }, []);

  const sendMessage = useCallback(async (request: string, context?: object) => {
    if (!request.trim() || isExecuting) return;

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: request,
      timestamp: new Date(),
    };

    const loadingId = crypto.randomUUID();
    const loadingMsg: ChatMessage = {
      id: loadingId,
      role: 'agent',
      content: '',
      timestamp: new Date(),
      isLoading: true,
    };

    setMessages(prev => [...prev, userMsg, loadingMsg]);
    setIsExecuting(true);

    const startTime = Date.now();

    abortRef.current = streamTask(
      request,
      {
        agentName: selectedAgent,
        conversationId,
        context,
        model: selectedModel || undefined,
      },
      {
        onStarted: (data) => {
          setMessages(prev =>
            prev.map(m =>
              m.id === loadingId
                ? { ...m, content: `Running ${data.agent || selectedAgent}...` }
                : m
            )
          );
        },
        onResult: (data) => {
          const elapsed = (Date.now() - startTime) / 1000;

          // Extract conversationId from result
          if (data.executionId && !conversationId) {
            setConversationId(data.executionId);
          }

          const resultContent = data.result;
          const contentStr = typeof resultContent === 'string'
            ? resultContent
            : JSON.stringify(resultContent, null, 2);

          const agentMsg: ChatMessage = {
            id: loadingId,
            role: 'agent',
            content: contentStr,
            timestamp: new Date(),
            result: resultContent,
            status: data.success ? 'SUCCESS' : 'FAILED',
            executionTime: elapsed,
            executionTrace: data.executionTrace,
            error: data.error,
          };

          setMessages(prev => prev.map(m => m.id === loadingId ? agentMsg : m));
        },
        onDone: () => {
          setIsExecuting(false);
          abortRef.current = null;
          refreshConversations();
        },
        onError: (error) => {
          const errorMsg: ChatMessage = {
            id: loadingId,
            role: 'agent',
            content: '',
            timestamp: new Date(),
            error,
            status: 'ERROR',
          };
          setMessages(prev => prev.map(m => m.id === loadingId ? errorMsg : m));
          setIsExecuting(false);
          abortRef.current = null;
          toast.error(error);
        },
      }
    );
  }, [conversationId, isExecuting, selectedAgent, selectedModel, refreshConversations]);

  const stopExecution = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsExecuting(false);
    setMessages(prev =>
      prev.map(m =>
        m.isLoading
          ? { ...m, isLoading: false, content: 'Cancelled', status: 'ERROR' as const, error: 'Execution cancelled' }
          : m
      )
    );
  }, []);

  const clearConversation = useCallback(() => {
    setMessages([]);
    setConversationId(null);
  }, []);

  const setSelectedAgent = useCallback((agentName: string) => {
    setSelectedAgentState(agentName);
    clearConversation();
  }, [clearConversation]);

  const loadConversation = useCallback(async (convId: string) => {
    try {
      const msgs = await getConversationMessages(convId);
      const chatMsgs: ChatMessage[] = msgs.map((m, i) => ({
        id: `${convId}-${i}`,
        role: m.role === 'user' ? 'user' as const : 'agent' as const,
        content: m.content,
        timestamp: new Date(),
      }));
      setMessages(chatMsgs);
      setConversationId(convId);
    } catch (err: any) {
      toast.error(err.message || 'Failed to load conversation');
    }
  }, []);

  const removeConversation = useCallback(async (convId: string) => {
    try {
      await deleteConversation(convId);
      if (conversationId === convId) {
        clearConversation();
      }
      refreshConversations();
    } catch (err: any) {
      toast.error(err.message || 'Failed to delete conversation');
    }
  }, [conversationId, clearConversation, refreshConversations]);

  return {
    messages,
    conversationId,
    isExecuting,
    systemInfo,
    selectedAgent,
    availableAgents,
    models,
    selectedModel,
    setSelectedModel,
    conversations,
    scrollRef,
    sendMessage,
    stopExecution,
    clearConversation,
    setSelectedAgent,
    loadConversation,
    removeConversation,
    refreshConversations,
  };
}
