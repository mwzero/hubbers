import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { buildCommand, getJarPath, getRepoPath, isJarFile } from '../util/config';

const MCP_CONFIG_PATH = '.vscode/mcp.json';
const SERVER_KEY = 'hubbers';

interface McpServerEntry {
    command: string;
    args: string[];
    env?: Record<string, string>;
    cwd?: string;
}

interface McpConfig {
    inputs?: unknown[];
    servers: Record<string, McpServerEntry>;
}

/**
 * Builds the MCP server entry based on the current jarPath and repoPath.
 * If jarPath ends with .jar, uses 'java -jar <path> mcp'.
 * Otherwise uses the executable directly (e.g., 'hubbers mcp' from PATH).
 * When repoPath is provided it is set as the working directory for the server.
 */
function buildMcpEntry(jarPath: string, repoPath?: string): McpServerEntry {
    const entry: McpServerEntry = isJarFile(jarPath)
        ? { command: 'java', args: ['-jar', jarPath, 'mcp'] }
        : { command: jarPath, args: ['mcp'] };
    if (repoPath) {
        entry.cwd = repoPath;
    }
    return entry;
}

/**
 * Command: Hubbers: Configure MCP Server
 *
 * Creates or updates .vscode/mcp.json with the Hubbers MCP server entry.
 * This registers the Hubbers stdio MCP server so that VS Code (1.99+) and
 * GitHub Copilot Chat can use all Hubbers tools, agents, and pipelines.
 */
export async function configureMcp(): Promise<void> {
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        vscode.window.showErrorMessage('No workspace folder is open. Open a folder first.');
        return;
    }

    let jarPath = getJarPath();

    // Prompt the user to provide jarPath if it is still the default 'hubbers'.
    if (jarPath === 'hubbers') {
        const configured = await promptForJarPath();
        if (configured === undefined) {
            return; // User cancelled
        }
        if (configured.length > 0) {
            jarPath = configured;
            await vscode.workspace
                .getConfiguration('hubbers')
                .update('jarPath', configured, vscode.ConfigurationTarget.Workspace);
        }
    }

    let repoPath = getRepoPath();

    // Prompt the user to provide repoPath if not yet configured.
    if (!repoPath) {
        const configured = await promptForRepoPath();
        if (configured === undefined) {
            return; // User cancelled
        }
        if (configured.length > 0) {
            repoPath = configured;
            await vscode.workspace
                .getConfiguration('hubbers')
                .update('repoPath', configured, vscode.ConfigurationTarget.Workspace);
        }
    }

    const entry = buildMcpEntry(jarPath, repoPath);
    const mcpFilePath = path.join(workspaceFolder.uri.fsPath, MCP_CONFIG_PATH);

    await writeMcpConfig(mcpFilePath, entry);

    const result = await vscode.window.showInformationMessage(
        `Hubbers MCP server registered in ${MCP_CONFIG_PATH}. Reload VS Code to activate it.`,
        'Reload Window',
        'Open mcp.json',
    );

    if (result === 'Reload Window') {
        vscode.commands.executeCommand('workbench.action.reloadWindow');
    } else if (result === 'Open mcp.json') {
        vscode.window.showTextDocument(vscode.Uri.file(mcpFilePath));
    }
}

/** Creates or updates .vscode/mcp.json preserving any existing servers. */
async function writeMcpConfig(mcpFilePath: string, entry: McpServerEntry): Promise<void> {
    const vscodeDir = path.dirname(mcpFilePath);
    if (!fs.existsSync(vscodeDir)) {
        fs.mkdirSync(vscodeDir, { recursive: true });
    }

    let config: McpConfig = { servers: {} };
    if (fs.existsSync(mcpFilePath)) {
        try {
            const raw = fs.readFileSync(mcpFilePath, 'utf8');
            config = JSON.parse(raw) as McpConfig;
            config.servers ??= {};
        } catch {
            // Corrupted config — overwrite it
        }
    }

    config.servers[SERVER_KEY] = entry;

    fs.writeFileSync(mcpFilePath, JSON.stringify(config, null, 2) + '\n', 'utf8');
}

/** Prompts the user for the artifact repository root path. Returns undefined if cancelled. */
async function promptForRepoPath(): Promise<string | undefined> {
    const choice = await vscode.window.showInformationMessage(
        'Where is your Hubbers artifact repository (agents/, tools/, pipelines/, skills/)?',
        { modal: true },
        'Browse for folder',
        'Enter path manually',
        'Use workspace root',
    );

    if (choice === 'Browse for folder') {
        const uris = await vscode.window.showOpenDialog({
            canSelectFiles: false,
            canSelectFolders: true,
            canSelectMany: false,
            title: 'Select Hubbers repository root',
        });
        return uris?.[0]?.fsPath;
    }

    if (choice === 'Enter path manually') {
        return vscode.window.showInputBox({
            prompt: 'Enter the path to the Hubbers artifact repository root',
            placeHolder: '/path/to/hubbers-repo',
            validateInput: (v) => (v.trim().length > 0 ? undefined : 'Path cannot be empty'),
        });
    }

    if (choice === 'Use workspace root') {
        return ''; // Keep using workspace root (no explicit repoPath)
    }

    return undefined; // Cancelled
}

/** Prompts the user for the jar/executable path. Returns undefined if cancelled. */
async function promptForJarPath(): Promise<string | undefined> {
    const choice = await vscode.window.showInformationMessage(
        "Hubbers couldn't find the CLI on PATH. How would you like to configure it?",
        { modal: true },
        'Browse for hubbers.jar',
        'Enter path manually',
        'Use "hubbers" from PATH',
    );

    if (choice === 'Browse for hubbers.jar') {
        const uris = await vscode.window.showOpenDialog({
            canSelectFiles: true,
            canSelectFolders: false,
            filters: { 'Java Archive': ['jar'] },
            title: 'Select hubbers.jar',
        });
        return uris?.[0]?.fsPath;
    }

    if (choice === 'Enter path manually') {
        return vscode.window.showInputBox({
            prompt: "Enter the path to hubbers.jar or the 'hubbers' executable",
            placeHolder: '/path/to/hubbers.jar',
            validateInput: (v) => (v.trim().length > 0 ? undefined : 'Path cannot be empty'),
        });
    }

    if (choice === 'Use "hubbers" from PATH') {
        return ''; // Keep using the default
    }

    return undefined; // Cancelled
}
