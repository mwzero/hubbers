import * as vscode from 'vscode';
import * as fs from 'fs';
import { ArtifactNode, InputFileNode } from '../providers/ArtifactTreeProvider';
import { detectArtifactName, detectArtifactType, scanInputFiles } from '../util/artifactScanner';
import { runHubbersInTerminal } from '../util/processRunner';
import { ArtifactType, TYPE_COMMANDS } from '../types';

/**
 * Command: Hubbers: Run Artifact
 *
 * Can be triggered from:
 * 1. Tree view context menu  → receives an ArtifactNode argument
 * 2. CodeLens "▶ Run"       → receives no argument (uses active editor)
 * 3. Command palette         → no argument (uses active editor)
 */
export async function runArtifact(node?: ArtifactNode | InputFileNode): Promise<void> {
    let artifactType: ArtifactType | undefined;
    let artifactName: string | undefined;
    let manifestFilePath: string | undefined;
    let specificInputFilePath: string | undefined;

    if (node instanceof InputFileNode) {
        // Called from an input file node — run immediately with that file
        artifactType = node.parentItem.type;
        artifactName = node.parentItem.name;
        specificInputFilePath = node.inputFile.filePath;
    } else if (node instanceof ArtifactNode) {
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
    const subCommand = TYPE_COMMANDS[artifactType];
    const args = [subCommand, 'run', artifactName];

    if (specificInputFilePath) {
        // InputFileNode: validate JSON then pass the path directly to the CLI
        if (!validateJsonFile(specificInputFilePath)) { return; }
        runHubbersInTerminal(args, undefined, specificInputFilePath);
    } else {
        const resolved = await resolveInput(artifactType, artifactName, manifestFilePath);
        if (resolved === undefined) { return; }
        runHubbersInTerminal(args, resolved.json, resolved.filePath);
    }
}

/**
 * Resolves the JSON input for a run.
 *
 * Returns:
 *   - `{ filePath }` when a JSON file was selected (path passed directly to CLI)
 *   - `{ json }` when the user typed JSON inline (written to temp file by processRunner)
 *   - `{}` for "no input"
 *   - `undefined` if the user cancelled
 */
async function resolveInput(
    artifactType: ArtifactType,
    artifactName: string,
    manifestFilePath: string | undefined,
): Promise<{ json?: string; filePath?: string } | undefined> {
    const inputFiles = manifestFilePath ? scanInputFiles(manifestFilePath) : [];

    // No pre-stored inputs → fall back to inline box (original behaviour)
    if (inputFiles.length === 0) {
        const json = await promptInline(artifactType, artifactName);
        if (json === undefined) { return undefined; }
        return { json: json.trim() || undefined };
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
        { label: '$(folder-opened) Browse for JSON file…', action: 'browse' },
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
        return {}; // No --input
    }

    if (picked.action === 'inline') {
        const json = await promptInline(artifactType, artifactName);
        if (json === undefined) { return undefined; }
        return { json: json.trim() || undefined };
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
        const fp = uris[0].fsPath;
        if (!validateJsonFile(fp)) { return undefined; }
        return { filePath: fp };
    }

    // A pre-stored input file was selected
    if (picked.filePath) {
        if (!validateJsonFile(picked.filePath)) { return undefined; }
        return { filePath: picked.filePath };
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
 * Validates that a file exists and contains valid JSON.
 * Shows an error message and returns false if not.
 */
function validateJsonFile(filePath: string): boolean {
    let raw: string;
    try {
        raw = fs.readFileSync(filePath, 'utf8');
    } catch {
        vscode.window.showErrorMessage(`Failed to read input file: ${filePath}`);
        return false;
    }
    try {
        JSON.parse(raw);
        return true;
    } catch {
        vscode.window.showErrorMessage(`Input file contains invalid JSON: ${filePath}`);
        return false;
    }
}
