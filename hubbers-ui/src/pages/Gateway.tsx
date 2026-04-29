import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Copy, Network, ShieldAlert, XCircle } from 'lucide-react';
import { fetchGatewayStatus } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { toast } from 'sonner';

function StatusBadge({ ok }: { ok: boolean }) {
  return ok ? (
    <Badge className="gap-1 text-[10px]"><CheckCircle2 className="h-3 w-3" /> enabled</Badge>
  ) : (
    <Badge variant="secondary" className="gap-1 text-[10px]"><XCircle className="h-3 w-3" /> disabled</Badge>
  );
}

function CopyButton({ text }: { text: string }) {
  return (
    <Button
      variant="outline"
      size="sm"
      className="h-7 text-xs gap-1"
      onClick={() => navigator.clipboard.writeText(text).then(() => toast.success('Copied'))}
    >
      <Copy className="h-3 w-3" /> Copy
    </Button>
  );
}

export default function Gateway() {
  const { data, isLoading } = useQuery({ queryKey: ['gateway-status'], queryFn: fetchGatewayStatus, refetchInterval: 10000 });

  const mcpConfig = `{
  "mcpServers": {
    "hubbers": {
      "url": "http://localhost:7070/mcp"
    }
  }
}`;

  return (
    <div className="min-h-screen bg-background">
      <header className="h-14 border-b bg-card flex items-center px-4 gap-3">
        <Link to="/">
          <Button variant="ghost" size="icon" className="h-8 w-8"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div className="flex items-center gap-2">
          <Network className="h-5 w-5" />
          <h1 className="text-lg font-semibold">Gateway</h1>
        </div>
      </header>

      <main className="mx-auto max-w-6xl p-6 space-y-6">
        {isLoading && <p className="text-sm text-muted-foreground">Loading gateway status...</p>}
        {data && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card>
                <CardHeader className="pb-2"><CardTitle className="text-sm">MCP</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  <StatusBadge ok={data.mcp.configured} />
                  <div className="font-mono text-xs text-muted-foreground space-y-1">
                    <p>{data.mcp.endpoint}</p>
                    <p>{data.mcp.sseEndpoint}</p>
                  </div>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="pb-2"><CardTitle className="text-sm">OpenAI-Compatible</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  <StatusBadge ok={data.openAiCompatible.configured} />
                  <div className="font-mono text-xs text-muted-foreground space-y-1">
                    <p>{data.openAiCompatible.modelsEndpoint}</p>
                    <p>{data.openAiCompatible.chatCompletionsEndpoint}</p>
                    <p>{data.openAiCompatible.toolsEndpoint}</p>
                  </div>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="pb-2"><CardTitle className="text-sm">Policy</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  <Badge variant={data.policy.apiKeyRequired ? 'default' : 'destructive'} className="gap-1 text-[10px]">
                    <ShieldAlert className="h-3 w-3" /> API key {data.policy.apiKeyRequired ? 'required' : 'not required'}
                  </Badge>
                  <p className="text-xs text-muted-foreground">Certified-only mode: {data.policy.certifiedOnly ? 'on' : 'off'}</p>
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm">Exposed Artifacts</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {Object.entries(data.policy.exposedArtifacts).map(([name, count]) => (
                  <div key={name} className="rounded-md border p-3">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">{name}</p>
                    <p className="text-2xl font-semibold">{count}</p>
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm">MCP Client Configuration</CardTitle>
                <CopyButton text={mcpConfig} />
              </CardHeader>
              <CardContent>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto"><code>{mcpConfig}</code></pre>
              </CardContent>
            </Card>
          </>
        )}
      </main>
    </div>
  );
}