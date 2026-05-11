import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { ArtifactNode, InputFileNode } from '../providers/ArtifactTreeProvider';

/**
 * Command: Hubbers: New Input File
 *
 * Creates a new JSON input file inside <artifact-dir>/inputs/ and opens it.
 */
export async function newInputFile(node: ArtifactNode, refresh: () => void): Promise<void> {
    const name = await vscode.window.showInputBox({
        prompt: 'Input file name (without .json)',
        placeHolder: 'default',
        value: 'default',
        validateInput: (v) => {
            if (!v.trim()) { return 'Name cannot be empty'; }
            if (/[/\\]/.test(v)) { return 'Name cannot contain path separators'; }
            return undefined;
        },
    });
    if (name === undefined) { return; }

    const inputsDir = path.join(path.dirname(node.item.filePath), 'inputs');
    const filePath = path.join(inputsDir, `${name.trim()}.json`);

    if (fs.existsSync(filePath)) {
        const answer = await vscode.window.showWarningMessage(
            `"${name.trim()}.json" already exists. Open it?`,
            'Open', 'Cancel',
        );
        if (answer === 'Open') {
            vscode.window.showTextDocument(vscode.Uri.file(filePath));
        }
        return;
    }

    try {
        if (!fs.existsSync(inputsDir)) {
            fs.mkdirSync(inputsDir, { recursive: true });
        }
        fs.writeFileSync(filePath, '{}\n', 'utf8');
    } catch (err) {
        vscode.window.showErrorMessage(
            `Failed to create input file: ${err instanceof Error ? err.message : String(err)}`,
        );
        return;
    }

    refresh();
    vscode.window.showTextDocument(vscode.Uri.file(filePath));
}

/**
 * Command: Hubbers: Delete Input File
 *
 * Deletes the selected input JSON file after user confirmation.
 */
export async function deleteInputFile(node: InputFileNode, refresh: () => void): Promise<void> {
    const answer = await vscode.window.showWarningMessage(
        `Delete input file "${node.inputFile.label}.json"?`,
        { modal: true },
        'Delete',
    );
    if (answer !== 'Delete') { return; }

    try {
        fs.unlinkSync(node.inputFile.filePath);
    } catch (err) {
        vscode.window.showErrorMessage(
            `Failed to delete: ${err instanceof Error ? err.message : String(err)}`,
        );
        return;
    }

    refresh();
}
