import { Layout, PanelLeft, PanelLeftClose, MessageSquare, Settings, Network } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

interface WorkspaceHeaderProps {
  apiOnline: boolean;
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
}

export function WorkspaceHeader({ apiOnline, sidebarOpen, onToggleSidebar }: WorkspaceHeaderProps) {
  return (
    <header className="h-14 border-b bg-card flex items-center px-4 gap-3 shrink-0">
      <div className="flex items-center gap-2">
        <div className="w-8 h-8 rounded-md bg-primary flex items-center justify-center">
          <Layout className="w-4 h-4 text-primary-foreground" />
        </div>
        <div className="flex flex-col">
          <h1 className="font-semibold text-sm tracking-tight leading-none">Hubbers Dev Workspace</h1>
          <span className="text-[9px] font-mono text-muted-foreground/60 leading-none mt-0.5">
            v{__APP_VERSION__} &middot; {__APP_BUILD_DATE__} &middot; #{__APP_BUILD_SERIAL__}
          </span>
        </div>
      </div>

      <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onToggleSidebar}>
        {sidebarOpen ? <PanelLeftClose className="w-4 h-4" /> : <PanelLeft className="w-4 h-4" />}
      </Button>

      <div className="ml-auto flex items-center gap-3">
        <Link to="/chat">
          <Button variant="ghost" size="sm" className="h-8 text-xs gap-1.5">
            <MessageSquare className="w-4 h-4" />
            Chat
          </Button>
        </Link>
        <Link to="/gateway">
          <Button variant="ghost" size="sm" className="h-8 text-xs gap-1.5">
            <Network className="w-4 h-4" />
            Gateway
          </Button>
        </Link>
        <Link to="/settings">
          <Button variant="ghost" size="sm" className="h-8 text-xs gap-1.5">
            <Settings className="w-4 h-4" />
            Settings
          </Button>
        </Link>
        <span className="relative flex h-2.5 w-2.5">
          <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${apiOnline ? 'bg-success' : 'bg-destructive'}`} />
          <span className={`relative inline-flex rounded-full h-2.5 w-2.5 ${apiOnline ? 'bg-success' : 'bg-destructive'}`} />
        </span>
        <span className="text-xs text-muted-foreground">
          API {apiOnline ? 'Online' : 'Offline'}
        </span>
      </div>
    </header>
  );
}
