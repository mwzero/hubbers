import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

const MCP_CONFIG_PATH = '.vscode/mcp.json';
const SERVER_KEY = 'hubbers';

/**
 * Checks whether the Hubbers MCP server is already configured in the
 * workspace's .vscode/mcp.json file and notifies the user if not.
 */
export class McpServerProvider {
    checkAndNotify(): void {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) {
            return;
        }

        const mcpFilePath = path.join(workspaceFolder.uri.fsPath, MCP_CONFIG_PATH);
        if (this.isMcpConfigured(mcpFilePath)) {
            return;
        }

        vscode.window
            .showInformationMessage(
                'Hubbers: MCP server is not configured. Register it to use Hubbers tools in GitHub Copilot Chat.',
                'Configure Now',
                'Remind Me Later',
            )
            .then((choice) => {
                if (choice === 'Configure Now') {
                    vscode.commands.executeCommand('hubbers.configureMcp');
                }
            });
    }

    /** Returns true when .vscode/mcp.json already contains a 'hubbers' server entry. */
    private isMcpConfigured(mcpFilePath: string): boolean {
        if (!fs.existsSync(mcpFilePath)) {
            return false;
        }
        try {
            const config = JSON.parse(fs.readFileSync(mcpFilePath, 'utf8'));
            return SERVER_KEY in (config?.servers ?? {});
        } catch {
            return false;
        }
    }
}
