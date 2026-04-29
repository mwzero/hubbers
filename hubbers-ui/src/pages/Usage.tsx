import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getUsage, type UsageStats } from '@/lib/taskApi';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend } from 'recharts';
import { Activity, Cpu, Cloud, RefreshCw } from 'lucide-react';

const COLORS = ['#22c55e', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6'];

export default function Usage() {
  const { data, isLoading, refetch } = useQuery<UsageStats>({
    queryKey: ['usage'],
    queryFn: getUsage,
    refetchInterval: 10_000,
  });

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center h-screen text-muted-foreground">
        Loading usage data...
      </div>
    );
  }

  const providerEntries = Object.entries(data.providers);
  const barData = providerEntries.map(([name, stats]) => ({
    name,
    prompt: stats.promptTokens,
    completion: stats.completionTokens,
  }));

  const pieData = providerEntries.map(([name, stats]) => ({
    name,
    value: stats.totalTokens,
  }));

  return (
    <div className="max-w-6xl mx-auto p-6 space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Token Usage Dashboard</h1>
          <p className="text-muted-foreground">Monitor LLM token consumption by provider</p>
        </div>
        <button onClick={() => refetch()} className="flex items-center gap-2 px-3 py-2 rounded-md border hover:bg-accent">
          <RefreshCw className="w-4 h-4" /> Refresh
        </button>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="rounded-lg border p-4">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <Activity className="w-4 h-4" /> Total Tokens
          </div>
          <div className="text-3xl font-bold">{data.totalTokens.toLocaleString()}</div>
        </div>
        <div className="rounded-lg border p-4">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <Cpu className="w-4 h-4" /> Ollama Status
          </div>
          <div className="text-xl font-semibold">
            {data.ollamaAvailable ? (
              <span className="text-green-500">Online ({data.ollamaModels?.length || 0} models)</span>
            ) : (
              <span className="text-red-400">Offline</span>
            )}
          </div>
        </div>
        <div className="rounded-lg border p-4">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <Cloud className="w-4 h-4" /> Active Providers
          </div>
          <div className="text-3xl font-bold">{providerEntries.length}</div>
        </div>
      </div>

      {/* Charts */}
      {providerEntries.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Bar chart */}
          <div className="rounded-lg border p-4">
            <h2 className="text-lg font-semibold mb-4">Tokens by Provider</h2>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={barData}>
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="prompt" fill="#3b82f6" name="Prompt" stackId="a" />
                <Bar dataKey="completion" fill="#22c55e" name="Completion" stackId="a" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Pie chart */}
          <div className="rounded-lg border p-4">
            <h2 className="text-lg font-semibold mb-4">Distribution</h2>
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100} label>
                  {pieData.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Ollama models table */}
      {data.ollamaAvailable && data.ollamaModels && data.ollamaModels.length > 0 && (
        <div className="rounded-lg border p-4">
          <h2 className="text-lg font-semibold mb-4">Local Ollama Models</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th className="text-left py-2 px-3">Model</th>
                <th className="text-right py-2 px-3">Size</th>
              </tr>
            </thead>
            <tbody>
              {data.ollamaModels.map((m) => (
                <tr key={m.name} className="border-b last:border-0">
                  <td className="py-2 px-3 font-mono">{m.name}</td>
                  <td className="py-2 px-3 text-right">{(m.sizeBytes / 1e9).toFixed(1)} GB</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Provider details */}
      {providerEntries.length > 0 && (
        <div className="rounded-lg border p-4">
          <h2 className="text-lg font-semibold mb-4">Provider Details</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th className="text-left py-2 px-3">Provider</th>
                <th className="text-right py-2 px-3">Prompt Tokens</th>
                <th className="text-right py-2 px-3">Completion Tokens</th>
                <th className="text-right py-2 px-3">Total</th>
              </tr>
            </thead>
            <tbody>
              {providerEntries.map(([name, stats]) => (
                <tr key={name} className="border-b last:border-0">
                  <td className="py-2 px-3 font-semibold">{name}</td>
                  <td className="py-2 px-3 text-right">{stats.promptTokens.toLocaleString()}</td>
                  <td className="py-2 px-3 text-right">{stats.completionTokens.toLocaleString()}</td>
                  <td className="py-2 px-3 text-right font-semibold">{stats.totalTokens.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {providerEntries.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          No token usage recorded yet. Run an agent or skill to see usage data.
        </div>
      )}
    </div>
  );
}
