import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { ArtifactItem, ArtifactType, TYPE_LABELS } from '../types';
import { getRepoPath } from '../util/config';

/** Manifest file name per type. */
const MANIFEST_FILES: Record<ArtifactType, string> = {
    agent: 'agent.yaml',
    tool: 'tool.yaml',
    pipeline: 'pipeline.yaml',
    skill: 'SKILL.md',
};

/** Destination subfolder inside the repo root per type. */
const DEST_FOLDERS: Record<ArtifactType, string> = {
    agent: 'agents',
    tool: 'tools',
    pipeline: 'pipelines',
    skill: 'skills',
};

interface FoundArtifact extends ArtifactItem {
    /** Absolute path to the artifact's directory (parent of the manifest file). */
    dir: string;
}

/**
 * Command: Hubbers: Import Artifacts
 *
 * 1. User picks a source folder via the OS file dialog.
 * 2. The folder is scanned recursively for Hubbers manifests.
 * 3. A multi-select QuickPick lets the user choose which artifacts to import.
 * 4. Each selected artifact directory is copied into the repo root under
 *    agents/ | tools/ | pipelines/ | skills/.
 * 5. The artifact tree view is refreshed.
 */
export async function importArtifacts(
    treeRefresh: () => void,
    sourceUri?: vscode.Uri,
): Promise<void> {
    // -------------------------------------------------------------------------
    // 1. Resolve source folder
    // -------------------------------------------------------------------------
    let sourceDir: string;

    if (sourceUri?.fsPath) {
        // Called from Explorer context menu on a folder
        sourceDir = sourceUri.fsPath;
    } else {
        const picked = await vscode.window.showOpenDialog({
            canSelectFiles: false,
            canSelectFolders: true,
            canSelectMany: false,
            title: 'Select folder containing Hubbers artifacts',
        });
        if (!picked || picked.length === 0) {
            return;
        }
        sourceDir = picked[0].fsPath;
    }

    // -------------------------------------------------------------------------
    // 2. Scan source folder for Hubbers manifests
    // -------------------------------------------------------------------------
    const found = await scanFolderForArtifacts(sourceDir);

    if (found.length === 0) {
        vscode.window.showInformationMessage(
            `No Hubbers artifacts found in "${sourceDir}". ` +
                'Expected agent.yaml, tool.yaml, pipeline.yaml, or SKILL.md files.',
        );
        return;
    }

    // -------------------------------------------------------------------------
    // 3. Multi-select QuickPick
    // -------------------------------------------------------------------------
    const items: (vscode.QuickPickItem & { artifact: FoundArtifact })[] = found.map((a) => ({
        label: a.name,
        description: TYPE_LABELS[a.type],
        detail: a.filePath,
        picked: true,
        artifact: a,
    }));

    const selected = await vscode.window.showQuickPick(items, {
        canPickMany: true,
        title: `Import Hubbers Artifacts from "${path.basename(sourceDir)}"`,
        placeHolder: 'Select artifacts to import (all selected by default)',
    });

    if (!selected || selected.length === 0) {
        return;
    }

    // -------------------------------------------------------------------------
    // 4. Resolve destination repo root
    // -------------------------------------------------------------------------
    let repoRoot =
        getRepoPath() ?? vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;

    if (!repoRoot) {
        const picked = await vscode.window.showOpenDialog({
            canSelectFiles: false,
            canSelectFolders: true,
            canSelectMany: false,
            title: 'Select destination repo folder (where agents/, tools/, pipelines/, skills/ will be created)',
        });
        if (!picked || picked.length === 0) {
            return;
        }
        repoRoot = picked[0].fsPath;

        // Offer to persist the choice so future commands work automatically.
        // If no workspace is open we fall back to Global (user) settings.
        const hasWorkspace = (vscode.workspace.workspaceFolders?.length ?? 0) > 0;
        const saveTarget = hasWorkspace
            ? vscode.ConfigurationTarget.Workspace
            : vscode.ConfigurationTarget.Global;
        const saveLabel = hasWorkspace ? 'Yes (workspace settings)' : 'Yes (user settings)';

        const save = await vscode.window.showInformationMessage(
            `Save "${repoRoot}" as hubbers.repoPath?`,
            saveLabel,
            'No, use once',
        );
        if (save === saveLabel) {
            await vscode.workspace
                .getConfiguration('hubbers')
                .update('repoPath', repoRoot, saveTarget);
        }
    }

    // -------------------------------------------------------------------------
    // 5. Copy artifacts with conflict resolution
    // -------------------------------------------------------------------------
    let importedCount = 0;
    let skippedCount = 0;

    for (const item of selected) {
        const artifact = item.artifact;
        const destTypeDir = path.join(repoRoot, DEST_FOLDERS[artifact.type]);
        const destArtifactDir = path.join(destTypeDir, artifact.name);

        if (fs.existsSync(destArtifactDir)) {
            const choice = await vscode.window.showWarningMessage(
                `"${artifact.name}" (${TYPE_LABELS[artifact.type]}) already exists in the repo.`,
                { modal: false },
                'Overwrite',
                'Skip',
            );
            if (choice !== 'Overwrite') {
                skippedCount++;
                continue;
            }
            // Remove existing directory before copying
            fs.rmSync(destArtifactDir, { recursive: true, force: true });
        }

        fs.mkdirSync(destTypeDir, { recursive: true });
        copyDirRecursive(artifact.dir, destArtifactDir);
        importedCount++;
    }

    // -------------------------------------------------------------------------
    // 6. Refresh tree view and notify
    // -------------------------------------------------------------------------
    treeRefresh();

    const parts: string[] = [];
    if (importedCount > 0) {
        parts.push(`${importedCount} artifact${importedCount !== 1 ? 's' : ''} imported`);
    }
    if (skippedCount > 0) {
        parts.push(`${skippedCount} skipped`);
    }
    vscode.window.showInformationMessage(`Hubbers: ${parts.join(', ')}.`);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Recursively scans a directory for Hubbers artifact manifests and returns
 * typed FoundArtifact entries. Does not cross into node_modules or .git.
 */
async function scanFolderForArtifacts(root: string): Promise<FoundArtifact[]> {
    const results: FoundArtifact[] = [];
    walkDir(root, results);
    return results;
}

/** Synchronous depth-first directory walk. */
function walkDir(dir: string, results: FoundArtifact[]): void {
    let entries: fs.Dirent[];
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
        return;
    }

    const base = path.basename(dir);
    if (base === 'node_modules' || base === '.git' || base === 'target') {
        return;
    }

    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            walkDir(fullPath, results);
        } else if (entry.isFile()) {
            const type = typeFromFileName(entry.name);
            if (type !== undefined) {
                results.push({
                    name: path.basename(dir),
                    type,
                    filePath: fullPath,
                    dir,
                });
            }
        }
    }
}

function typeFromFileName(fileName: string): ArtifactType | undefined {
    switch (fileName) {
        case 'agent.yaml':    return 'agent';
        case 'tool.yaml':     return 'tool';
        case 'pipeline.yaml': return 'pipeline';
        case 'SKILL.md':      return 'skill';
        default:              return undefined;
    }
}

/** Recursively copies srcDir into destDir (destDir must not exist yet). */
function copyDirRecursive(srcDir: string, destDir: string): void {
    fs.mkdirSync(destDir, { recursive: true });
    const entries = fs.readdirSync(srcDir, { withFileTypes: true });
    for (const entry of entries) {
        const src = path.join(srcDir, entry.name);
        const dest = path.join(destDir, entry.name);
        if (entry.isDirectory()) {
            copyDirRecursive(src, dest);
        } else {
            fs.copyFileSync(src, dest);
        }
    }
}
