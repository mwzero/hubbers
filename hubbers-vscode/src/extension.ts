import * as vscode from 'vscode';
import { ArtifactTreeProvider, ArtifactNode, InputFileNode } from './providers/ArtifactTreeProvider';
import { ArtifactCodeLensProvider } from './providers/ArtifactCodeLensProvider';
import { McpServerProvider } from './providers/McpServerProvider';
import { configureMcp } from './commands/configureMcp';
import { runArtifact } from './commands/runArtifact';
import { importArtifacts } from './commands/importArtifacts';
import { newInputFile, deleteInputFile } from './commands/manageInputs';
import { newArtifact, newArtifactOfType } from './commands/newArtifact';
import { registerSchemas } from './util/schemaRegistrar';
import { getOutputChannel } from './util/processRunner';

export function activate(context: vscode.ExtensionContext): void {
    // -------------------------------------------------------------------------
    // Artifact tree view (sidebar)
    // -------------------------------------------------------------------------
    const treeProvider = new ArtifactTreeProvider();
    const treeView = vscode.window.createTreeView('hubbersArtifacts', {
        treeDataProvider: treeProvider,
        showCollapseAll: true,
    });
    context.subscriptions.push(treeView, treeProvider);

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------
    context.subscriptions.push(
        vscode.commands.registerCommand('hubbers.refreshArtifacts', () =>
            treeProvider.refresh(),
        ),

        vscode.commands.registerCommand('hubbers.runArtifact', (node: ArtifactNode | InputFileNode) =>
            runArtifact(node),
        ),

        // Called from CodeLens and command palette — no node argument
        vscode.commands.registerCommand('hubbers.runCurrentArtifact', () =>
            runArtifact(undefined),
        ),

        vscode.commands.registerCommand('hubbers.openArtifact', (node: ArtifactNode) => {
            if (node?.uri) {
                vscode.window.showTextDocument(node.uri);
            }
        }),

        vscode.commands.registerCommand('hubbers.configureMcp', () => configureMcp()),

        vscode.commands.registerCommand('hubbers.importArtifacts', (uri?: vscode.Uri) =>
            importArtifacts(() => treeProvider.refresh(), uri),
        ),

        vscode.commands.registerCommand('hubbers.newInputFile', (node: ArtifactNode) =>
            newInputFile(node, () => treeProvider.refresh()),
        ),

        vscode.commands.registerCommand('hubbers.deleteInputFile', (node: InputFileNode) =>
            deleteInputFile(node, () => treeProvider.refresh()),
        ),

        vscode.commands.registerCommand('hubbers.newArtifact', () =>
            newArtifact(() => treeProvider.refresh()),
        ),

        vscode.commands.registerCommand('hubbers.newAgent', () =>
            newArtifactOfType('agent', () => treeProvider.refresh()),
        ),

        vscode.commands.registerCommand('hubbers.newTool', () =>
            newArtifactOfType('tool', () => treeProvider.refresh()),
        ),

        vscode.commands.registerCommand('hubbers.newPipeline', () =>
            newArtifactOfType('pipeline', () => treeProvider.refresh()),
        ),

        vscode.commands.registerCommand('hubbers.newSkill', () =>
            newArtifactOfType('skill', () => treeProvider.refresh()),
        ),
    );

    // -------------------------------------------------------------------------
    // CodeLens — "▶ Run" above artifact manifests
    // -------------------------------------------------------------------------
    context.subscriptions.push(
        vscode.languages.registerCodeLensProvider(
            ArtifactCodeLensProvider.documentSelector,
            new ArtifactCodeLensProvider(),
        ),
    );

    // -------------------------------------------------------------------------
    // YAML schema registration (requires redhat.vscode-yaml when installed)
    // -------------------------------------------------------------------------
    registerSchemas(context);

    // -------------------------------------------------------------------------
    // MCP server check — notify user if not yet configured
    // -------------------------------------------------------------------------
    new McpServerProvider().checkAndNotify();

    // -------------------------------------------------------------------------
    // Status bar item
    // -------------------------------------------------------------------------
    const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
    statusBar.text = '$(hubot) Hubbers';
    statusBar.tooltip = 'Hubbers: Click to configure MCP server';
    statusBar.command = 'hubbers.configureMcp';
    statusBar.show();
    context.subscriptions.push(statusBar);

    // Ensure the output channel is created and ready
    context.subscriptions.push(getOutputChannel());
}

export function deactivate(): void {
    // No explicit teardown needed — VS Code disposes context.subscriptions automatically.
}
