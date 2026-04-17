import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useToast } from '@/hooks/use-toast';
import { fetchSettings, saveSettings } from '@/lib/api';
import type { AppConfig } from '@/types/settings';
import { Loader2, ArrowLeft, Save, Settings as SettingsIcon } from 'lucide-react';

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
              <TabsTrigger value="openai">OpenAI</TabsTrigger>
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
                  <div className="space-y-2">
                    <Label htmlFor="openai-key">API Key</Label>
                    <Input
                      id="openai-key"
                      type="password"
                      value={config.openai?.apiKey || ''}
                      onChange={(e) => setConfig({
                        ...config,
                        openai: { ...config.openai, apiKey: e.target.value }
                      })}
                      placeholder="sk-..."
                    />
                    <p className="text-sm text-muted-foreground">
                      Your OpenAI API key (supports ${'{'}OPENAI_API_KEY{'}'} variable)
                    </p>
                  </div>

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
