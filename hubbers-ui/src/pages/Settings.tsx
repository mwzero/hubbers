import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { useToast } from '@/hooks/use-toast';
import { fetchSettings, saveSettings } from '@/lib/api';
import { SecretField } from '@/components/settings/SecretField';
import type { AppConfig } from '@/types/settings';
import { Loader2, ArrowLeft, Save, Settings as SettingsIcon, Database } from 'lucide-react';

export default function Settings() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      setLoading(true);
      const data = await fetchSettings();
      setConfig(data);
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error loading settings',
        description: error instanceof Error ? error.message : 'Unknown error',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!config) return;

    try {
      setSaving(true);
      await saveSettings(config);
      toast({
        title: 'Settings saved',
        description: 'Configuration updated successfully. Restart the server for changes to take effect.',
      });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Error saving settings',
        description: error instanceof Error ? error.message : 'Unknown error',
      });
    } finally {
      setSaving(false);
    }
  };

  const splitList = (value: string) => value.split(',').map(item => item.trim()).filter(Boolean);
  const joinList = (value?: string[]) => (value || []).join(', ');
  const vectorDb = config?.vectorDb || {
    enabled: true,
    provider: 'lucene',
    rootPath: './datasets/lucene/vector',
    defaultIndex: 'default',
    embeddingStrategy: 'hashing',
    dimensions: 256,
    defaultTopK: 3,
    certifiedOnly: false,
    retentionDays: 365,
    allowedPaths: ['./datasets/lucene'],
  };

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!config) {
    return (
      <div className="h-screen flex items-center justify-center">
        <p className="text-muted-foreground">Failed to load settings</p>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <div className="flex items-center gap-2">
              <SettingsIcon className="h-6 w-6" />
              <h1 className="text-2xl font-bold">Settings</h1>
            </div>
          </div>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : (
              <>
                <Save className="mr-2 h-4 w-4" />
                Save Changes
              </>
            )}
          </Button>
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 overflow-auto">
        <div className="container mx-auto px-4 py-8 max-w-4xl">
          <Tabs defaultValue="ollama" className="space-y-6">
            <TabsList>
              <TabsTrigger value="ollama">Ollama</TabsTrigger>
              <TabsTrigger value="llama">llama.cpp</TabsTrigger>
              <TabsTrigger value="openai">OpenAI</TabsTrigger>
              <TabsTrigger value="vector-db">Vector DB</TabsTrigger>
              <TabsTrigger value="security">Security</TabsTrigger>
              <TabsTrigger value="executions">Executions</TabsTrigger>
            </TabsList>

            {/* Ollama Settings */}
            <TabsContent value="ollama" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Ollama Configuration</CardTitle>
                  <CardDescription>
                    Configure your local Ollama instance for running LLM models
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="ollama-url">Base URL</Label>
                    <Input
                      id="ollama-url"
                      value={config.ollama?.baseUrl || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        ollama: { ...config.ollama, baseUrl: e.target.value }
                      })}
                      placeholder="http://localhost:11434"
                    />
                    <p className="text-sm text-muted-foreground">
                      The base URL for your Ollama API endpoint
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="ollama-model">Default Model</Label>
                    <Input
                      id="ollama-model"
                      value={config.ollama?.defaultModel || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        ollama: { ...config.ollama, defaultModel: e.target.value }
                      })}
                      placeholder="gemma4"
                    />
                    <p className="text-sm text-muted-foreground">
                      The default model to use when none is specified
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="ollama-timeout">Request Timeout (seconds)</Label>
                    <Input
                      id="ollama-timeout"
                      type="number"
                      value={config.ollama?.timeoutSeconds || 120}
                      onChange={(e) => setConfig({
                        ...config,
                        ollama: { 
                          ...config.ollama, 
                          timeoutSeconds: parseInt(e.target.value) || 120 
                        }
                      })}
                      placeholder="120"
                      min="10"
                      max="600"
                    />
                    <p className="text-sm text-muted-foreground">
                      Maximum time to wait for model responses (10-600 seconds, default: 120)
                    </p>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            {/* llama.cpp Settings */}
            <TabsContent value="llama" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>llama.cpp Configuration</CardTitle>
                  <CardDescription>
                    Configure a local llama.cpp server that exposes OpenAI-compatible endpoints
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="llama-url">Base URL</Label>
                    <Input
                      id="llama-url"
                      value={config.llamaCpp?.baseUrl || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        llamaCpp: { ...config.llamaCpp, baseUrl: e.target.value }
                      })}
                      placeholder="http://localhost:8080"
                    />
                    <p className="text-sm text-muted-foreground">
                      The llama.cpp server URL, usually from llama-server or llama-cpp-python
                    </p>
                  </div>

                  <SecretField
                    id="llama-key"
                    label="API Key"
                    configured={Boolean(config.llamaCpp?.apiKey)}
                    placeholder="Optional"
                    onChange={(value) => setConfig({
                      ...config,
                      llamaCpp: { ...config.llamaCpp, apiKey: value }
                    })}
                    description="Only needed when the llama.cpp server is started with an API key."
                  />

                  <div className="space-y-2">
                    <Label htmlFor="llama-model">Default Model Alias</Label>
                    <Input
                      id="llama-model"
                      value={config.llamaCpp?.defaultModel || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        llamaCpp: { ...config.llamaCpp, defaultModel: e.target.value }
                      })}
                      placeholder="default"
                    />
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            {/* OpenAI Settings */}
            <TabsContent value="openai" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>OpenAI Configuration</CardTitle>
                  <CardDescription>
                    Configure OpenAI API for cloud-based LLM models
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <SecretField
                    id="openai-key"
                    label="API Key"
                    configured={Boolean(config.openai?.apiKey)}
                    placeholder="sk-..."
                    onChange={(value) => setConfig({
                      ...config,
                      openai: { ...config.openai, apiKey: value }
                    })}
                    description="Your OpenAI API key. Existing values are not displayed after load."
                  />

                  <div className="space-y-2">
                    <Label htmlFor="openai-url">Base URL</Label>
                    <Input
                      id="openai-url"
                      value={config.openai?.baseUrl || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        openai: { ...config.openai, baseUrl: e.target.value }
                      })}
                      placeholder="https://api.openai.com/v1"
                    />
                    <p className="text-sm text-muted-foreground">
                      The base URL for OpenAI API (or compatible endpoints)
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="openai-model">Default Model</Label>
                    <Input
                      id="openai-model"
                      value={config.openai?.defaultModel || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        openai: { ...config.openai, defaultModel: e.target.value }
                      })}
                      placeholder="gpt-4.1-mini"
                    />
                    <p className="text-sm text-muted-foreground">
                      The default OpenAI model to use
                    </p>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            {/* Vector DB Settings */}
            <TabsContent value="vector-db" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Database className="h-5 w-5" />
                    Vector Database Configuration
                  </CardTitle>
                  <CardDescription>
                    Manage the native local vector store used by retrieval, enrichment, agents, and pipelines
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center justify-between rounded-md border p-3">
                    <div className="space-y-1">
                      <Label htmlFor="vector-enabled">Enable Vector DB Management</Label>
                      <p className="text-sm text-muted-foreground">
                        Enables UI and API management for local vector indexes.
                      </p>
                    </div>
                    <Switch
                      id="vector-enabled"
                      checked={vectorDb.enabled !== false}
                      onCheckedChange={(enabled) => setConfig({
                        ...config,
                        vectorDb: { ...vectorDb, enabled }
                      })}
                    />
                  </div>

                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="space-y-2">
                      <Label htmlFor="vector-provider">Provider</Label>
                      <Select
                        value={vectorDb.provider || 'lucene'}
                        onValueChange={(provider) => setConfig({
                          ...config,
                          vectorDb: { ...vectorDb, provider }
                        })}
                      >
                        <SelectTrigger id="vector-provider">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="lucene">Lucene (native local)</SelectItem>
                        </SelectContent>
                      </Select>
                      <p className="text-sm text-muted-foreground">
                        Lucene is embedded, local-first, and requires no external service.
                      </p>
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="vector-embedding">Embedding Strategy</Label>
                      <Select
                        value={vectorDb.embeddingStrategy || 'hashing'}
                        onValueChange={(embeddingStrategy) => setConfig({
                          ...config,
                          vectorDb: { ...vectorDb, embeddingStrategy }
                        })}
                      >
                        <SelectTrigger id="vector-embedding">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="hashing">Deterministic hashing (built-in)</SelectItem>
                          <SelectItem value="model-provider" disabled>Model provider embeddings (planned)</SelectItem>
                        </SelectContent>
                      </Select>
                      <p className="text-sm text-muted-foreground">
                        Current Lucene tools use a deterministic local embedding strategy.
                      </p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="vector-root">Managed Index Root Path</Label>
                    <Input
                      id="vector-root"
                      value={vectorDb.rootPath || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        vectorDb: { ...vectorDb, rootPath: e.target.value }
                      })}
                      placeholder="./datasets/lucene/vector"
                    />
                    <p className="text-sm text-muted-foreground">
                      Base directory for managed vector indexes. Keep this inside approved local storage.
                    </p>
                  </div>

                  <div className="grid gap-4 md:grid-cols-4">
                    <div className="space-y-2 md:col-span-1">
                      <Label htmlFor="vector-default-index">Default Index</Label>
                      <Input
                        id="vector-default-index"
                        value={vectorDb.defaultIndex || ''}
                        onChange={(e) => setConfig({
                          ...config,
                          vectorDb: { ...vectorDb, defaultIndex: e.target.value }
                        })}
                        placeholder="default"
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="vector-dimensions">Dimensions</Label>
                      <Input
                        id="vector-dimensions"
                        type="number"
                        value={vectorDb.dimensions || 256}
                        onChange={(e) => setConfig({
                          ...config,
                          vectorDb: { ...vectorDb, dimensions: parseInt(e.target.value) || 256 }
                        })}
                        min="1"
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="vector-top-k">Default Top K</Label>
                      <Input
                        id="vector-top-k"
                        type="number"
                        value={vectorDb.defaultTopK || 3}
                        onChange={(e) => setConfig({
                          ...config,
                          vectorDb: { ...vectorDb, defaultTopK: parseInt(e.target.value) || 3 }
                        })}
                        min="1"
                        max="100"
                      />
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="vector-retention">Retention Days</Label>
                      <Input
                        id="vector-retention"
                        type="number"
                        value={vectorDb.retentionDays || 365}
                        onChange={(e) => setConfig({
                          ...config,
                          vectorDb: { ...vectorDb, retentionDays: parseInt(e.target.value) || 365 }
                        })}
                        min="1"
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="vector-paths">Allowed Index Paths</Label>
                    <Input
                      id="vector-paths"
                      value={joinList(vectorDb.allowedPaths)}
                      onChange={(e) => setConfig({
                        ...config,
                        vectorDb: { ...vectorDb, allowedPaths: splitList(e.target.value) }
                      })}
                      placeholder="./datasets/lucene, ./repo/vector"
                    />
                    <p className="text-sm text-muted-foreground">
                      Comma-separated local paths where managed vector indexes may be created.
                    </p>
                  </div>

                  <div className="flex items-center justify-between rounded-md border p-3">
                    <div className="space-y-1">
                      <Label htmlFor="vector-certified">Certified Indexes Only</Label>
                      <p className="text-sm text-muted-foreground">
                        Restricts production retrieval flows to approved vector indexes.
                      </p>
                    </div>
                    <Switch
                      id="vector-certified"
                      checked={Boolean(vectorDb.certifiedOnly)}
                      onCheckedChange={(certifiedOnly) => setConfig({
                        ...config,
                        vectorDb: { ...vectorDb, certifiedOnly }
                      })}
                    />
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            {/* Security Settings */}
            <TabsContent value="security" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Security Policy</CardTitle>
                  <CardDescription>
                    Configure API access and high-risk tool restrictions for the local runtime
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <SecretField
                    id="api-key"
                    label="Web API Key"
                    configured={Boolean(config.security?.apiKey)}
                    placeholder="Bearer token"
                    onChange={(value) => setConfig({
                      ...config,
                      security: { ...config.security, apiKey: value }
                    })}
                    description="When set, /api/* endpoints require an Authorization: Bearer token header."
                  />

                  <div className="space-y-2">
                    <Label htmlFor="allowed-tools">Allowed Tools</Label>
                    <Input
                      id="allowed-tools"
                      value={joinList(config.security?.allowedTools)}
                      onChange={(e) => setConfig({
                        ...config,
                        security: { ...config.security, allowedTools: splitList(e.target.value) }
                      })}
                      placeholder="http, csv.read, rss"
                    />
                    <p className="text-sm text-muted-foreground">
                      Optional comma-separated allowlist. If populated, only these tool driver types can run.
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="denied-tools">Denied Tools</Label>
                    <Input
                      id="denied-tools"
                      value={joinList(config.security?.deniedTools)}
                      onChange={(e) => setConfig({
                        ...config,
                        security: { ...config.security, deniedTools: splitList(e.target.value) }
                      })}
                      placeholder="shell.exec, process.manage, file.ops"
                    />
                    <p className="text-sm text-muted-foreground">
                      Comma-separated denylist. These tool driver types are blocked even if also allowed.
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="allowed-commands">Allowed Shell Commands</Label>
                    <Input
                      id="allowed-commands"
                      value={joinList(config.security?.allowedCommands)}
                      onChange={(e) => setConfig({
                        ...config,
                        security: { ...config.security, allowedCommands: splitList(e.target.value) }
                      })}
                      placeholder="^git status$, ^mvn test$"
                    />
                    <p className="text-sm text-muted-foreground">
                      Optional regex allowlist for shell.exec commands.
                    </p>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            {/* Executions Settings */}
            <TabsContent value="executions" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Execution Configuration</CardTitle>
                  <CardDescription>
                    Configure execution storage and management settings
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="exec-path">Storage Path</Label>
                    <Input
                      id="exec-path"
                      value={config.executions?.path || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        executions: { ...config.executions, path: e.target.value }
                      })}
                      placeholder="./_executions"
                    />
                    <p className="text-sm text-muted-foreground">
                      Directory path for storing execution logs and artifacts
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="exec-retention">Retention Days</Label>
                    <Input
                      id="exec-retention"
                      type="number"
                      value={config.executions?.retentionDays || 30}
                      onChange={(e) => setConfig({
                        ...config,
                        executions: { 
                          ...config.executions, 
                          retentionDays: parseInt(e.target.value) || 30 
                        }
                      })}
                      placeholder="30"
                      min="1"
                    />
                    <p className="text-sm text-muted-foreground">
                      Number of days to retain execution history
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="exec-concurrent">Max Concurrent Executions</Label>
                    <Input
                      id="exec-concurrent"
                      type="number"
                      value={config.executions?.maxConcurrent || 10}
                      onChange={(e) => setConfig({
                        ...config,
                        executions: { 
                          ...config.executions, 
                          maxConcurrent: parseInt(e.target.value) || 10 
                        }
                      })}
                      placeholder="10"
                      min="1"
                      max="100"
                    />
                    <p className="text-sm text-muted-foreground">
                      Maximum number of concurrent executions allowed
                    </p>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>

          <div className="mt-6 p-4 bg-muted rounded-lg">
            <p className="text-sm text-muted-foreground">
              <strong>ℹ️ Note:</strong> Changes to these settings require a server restart to take effect.
              The configuration is saved to <code className="bg-background px-1 py-0.5 rounded">application.yaml</code>.
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
