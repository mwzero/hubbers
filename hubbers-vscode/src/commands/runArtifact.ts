import * as vscode from 'vscode';
import * as fs from 'fs';
import { ArtifactNode } from '../providers/ArtifactTreeProvider';
import { detectArtifactName, detectArtifactType, scanInputFiles } from '../util/artifactScanner';
import { runHubbersCommand, getOutputChannel, HubbersNotFoundError } from '../util/processRunner';
import { ArtifactType, TYPE_COMMANDS } from '../types';

/**
 * Command: Hubbers: Run Artifact
 *
 * Can be triggered from:
 * 1. Tree view context menu  → receives an ArtifactNode argument
 * 2. CodeLens "▶ Run"       → receives no argument (uses active editor)
 * 3. Command palette         → no argument (uses active editor)
 */
export async function runArtifact(node?: ArtifactNode): Promise<void> {
    let artifactType: ArtifactType | undefined;
    let artifactName: string | undefined;
    let manifestFilePath: string | undefined;

    if (node instanceof ArtifactNode) {
        // Called from tree view — node carries full artifact info
        artifactType = node.item.type;
        artifactName = node.item.name;
        manifestFilePath = node.item.filePath;
    } else {
        // Called from CodeLens or command palette — infer from active editor
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showErrorMessage('No file is open. Open an agent.yaml, tool.yaml, pipeline.yaml, or SKILL.md first.');
            return;
        }
        artifactType = detectArtifactType(editor.document);
        artifactName = detectArtifactName(editor.document);
        manifestFilePath = editor.document.uri.fsPath;
    }

    if (!artifactType || !artifactName) {
        vscode.window.showErrorMessage(
            'Could not determine artifact type. Make sure the active file is a Hubbers manifest.',
        );
        return;
    }

    // Resolve the JSON input to pass to the CLI
    const inputJson = await resolveInput(artifactType, artifactName, manifestFilePath);
    if (inputJson === undefined) {
        return; // User cancelled
    }

    const subCommand = TYPE_COMMANDS[artifactType];
    const args = [subCommand, 'run', artifactName];

    try {
        await runHubbersCommand(args, inputJson.trim() || undefined);
    } catch (err) {
        if (err instanceof HubbersNotFoundError) {
            const choice = await vscode.window.showErrorMessage(
                err.message,
                'Configure hubbers.jarPath',
                'Show Output',
            );
            if (choice === 'Configure hubbers.jarPath') {
                vscode.commands.executeCommand(
                    'workbench.action.openSettings',
                    'hubbers.jarPath',
                );
            } else if (choice === 'Show Output') {
                getOutputChannel().show();
            }
        } else {
            const message = err instanceof Error ? err.message : String(err);
            vscode.window
                .showErrorMessage(`Hubbers run failed: ${message}`, 'Show Output')
                .then((choice) => {
                    if (choice === 'Show Output') {
                        getOutputChannel().show();
                    }
                });
        }
    }
}

/**
 * Resolves the JSON input string for a run.
 *
 * - When pre-stored input files exist in <artifact-dir>/inputs/, shows a
 *   quick pick with those files plus inline / browse / no-input options.
 * - When no input files exist, falls back directly to the inline input box
 *   (backward-compatible behaviour).
 *
 * Returns:
 *   - a JSON string (possibly empty) to pass via --input
 *   - undefined if the user cancelled
 */
async function resolveInput(
    artifactType: ArtifactType,
    artifactName: string,
    manifestFilePath: string | undefined,
): Promise<string | undefined> {
    const inputFiles = manifestFilePath ? scanInputFiles(manifestFilePath) : [];

    // No pre-stored inputs → fall back to inline box (original behaviour)
    if (inputFiles.length === 0) {
        return promptInline(artifactType, artifactName);
    }

    // Build quick-pick items
    type InputPickItem = vscode.QuickPickItem & { filePath?: string; action?: 'inline' | 'browse' | 'none' };

    const fileItems: InputPickItem[] = inputFiles.map((f) => ({
        label: `$(file-code) ${f.label}`,
        description: 'input file',
        filePath: f.filePath,
    }));

    const actionItems: InputPickItem[] = [
        { label: '', kind: vscode.QuickPickItemKind.Separator },
        { label: '$(pencil) Type JSON inline', action: 'inline' },
        { label: '$(folder-opened) Browse for JSON file\u2026', action: 'browse' },
        { label: '$(circle-slash) No input', action: 'none' },
    ];

    const picked = await vscode.window.showQuickPick<InputPickItem>(
        [...fileItems, ...actionItems],
        {
            title: `Input for ${artifactType} "${artifactName}"`,
            placeHolder: 'Select an input file or choose an option',
        },
    );

    if (!picked) {
        return undefined; // Cancelled
    }

    if (picked.action === 'none') {
        return ''; // Runs without --input
    }

    if (picked.action === 'inline') {
        return promptInline(artifactType, artifactName);
    }

    if (picked.action === 'browse') {
        const uris = await vscode.window.showOpenDialog({
            canSelectFiles: true,
            canSelectFolders: false,
            canSelectMany: false,
            filters: { 'JSON files': ['json'] },
            title: 'Select JSON input file',
        });
        if (!uris || uris.length === 0) {
            return undefined; // Cancelled
        }
        return readAndValidateJsonFile(uris[0].fsPath);
    }

    // A pre-stored input file was selected
    if (picked.filePath) {
        return readAndValidateJsonFile(picked.filePath);
    }

    return undefined;
}

/** Opens an inline JSON input box. Returns undefined when the user cancels. */
async function promptInline(artifactType: ArtifactType, artifactName: string): Promise<string | undefined> {
    return vscode.window.showInputBox({
        prompt: `Input JSON for ${artifactType} "${artifactName}" (leave empty for no input)`,
        placeHolder: '{"request": "Hello world"}',
        validateInput: (v) => {
            if (v.trim() === '') {
                return undefined;
            }
            try {
                JSON.parse(v);
                return undefined;
            } catch {
                return 'Invalid JSON';
            }
        },
    });
}

/**
 * Reads a JSON file from disk and validates its content.
 * Shows an error message and returns undefined if reading or parsing fails.
 */
function readAndValidateJsonFile(filePath: string): string | undefined {
    let raw: string;
    try {
        raw = fs.readFileSync(filePath, 'utf8');
    } catch (err) {
        vscode.window.showErrorMessage(`Failed to read input file: ${filePath}`);
        return undefined;
    }
    try {
        JSON.parse(raw);
    } catch {
        vscode.window.showErrorMessage(
            `Input file contains invalid JSON: ${filePath}`,
        );
        return undefined;
    }
    return raw;
}
