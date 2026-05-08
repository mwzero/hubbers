import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { ArtifactItem, ArtifactType, InputFile } from '../types';
import { getRepoPath } from './config';

/** File name for each artifact type. */
const ARTIFACT_FILES: Record<ArtifactType, string> = {
    agent: 'agent.yaml',
    tool: 'tool.yaml',
    pipeline: 'pipeline.yaml',
    skill: 'SKILL.md',
};

/** Glob patterns to exclude from searches. */
const EXCLUDE_PATTERNS = '{**/node_modules/**,**/target/**,**/.git/**}';

/**
 * Scans all workspace folders (or the configured repo path) for Hubbers
 * artifact manifest files and returns a typed list.
 */
export async function scanWorkspaceArtifacts(): Promise<ArtifactItem[]> {
    const repoPath = getRepoPath();
    const results: ArtifactItem[] = [];

    const artifactTypes: ArtifactType[] = ['agent', 'tool', 'pipeline', 'skill'];

    await Promise.all(
        artifactTypes.map(async (type) => {
            const fileName = ARTIFACT_FILES[type];
            const pattern = repoPath
                ? new vscode.RelativePattern(repoPath, `**/${fileName}`)
                : `**/${fileName}`;

            const uris = await vscode.workspace.findFiles(
                pattern as vscode.GlobPattern,
                EXCLUDE_PATTERNS,
            );

            for (const uri of uris) {
                const name = extractArtifactName(uri.fsPath, fileName);
                results.push({ name, type, filePath: uri.fsPath });
            }
        }),
    );

    return results.sort((a, b) =>
        a.type.localeCompare(b.type) || a.name.localeCompare(b.name),
    );
}

/**
 * Derives the artifact name from its manifest file path.
 * Convention: the parent directory of the manifest file is the artifact name.
 * e.g., '/repo/agents/universal.task/agent.yaml' → 'universal.task'
 */
function extractArtifactName(filePath: string, _fileName: string): string {
    const dir = path.dirname(filePath);
    return path.basename(dir);
}

/**
 * Returns pre-stored JSON input files for an artifact.
 * Convention: input files live at <artifact-dir>/inputs/*.json
 * e.g., 'agents/my-agent/inputs/default.json'
 *
 * Returns an empty array when the inputs/ folder does not exist.
 */
export function scanInputFiles(manifestFilePath: string): InputFile[] {
    const inputsDir = path.join(path.dirname(manifestFilePath), 'inputs');
    if (!fs.existsSync(inputsDir)) {
        return [];
    }
    let entries: fs.Dirent[];
    try {
        entries = fs.readdirSync(inputsDir, { withFileTypes: true });
    } catch {
        return [];
    }
    return entries
        .filter((e) => e.isFile() && e.name.toLowerCase().endsWith('.json'))
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((e) => ({
            label: path.basename(e.name, '.json'),
            filePath: path.join(inputsDir, e.name),
        }));
}

/**
 * Returns the artifact type for a given open document, or undefined if
 * the document is not a Hubbers manifest.
 */
export function detectArtifactType(document: vscode.TextDocument): ArtifactType | undefined {
    const fileName = path.basename(document.fileName);
    for (const [type, manifestFile] of Object.entries(ARTIFACT_FILES) as [ArtifactType, string][]) {
        if (fileName === manifestFile) {
            return type;
        }
    }
    return undefined;
}

/**
 * Returns the artifact name for the document's parent directory
 * (the same logic as extractArtifactName but operating on a TextDocument).
 */
export function detectArtifactName(document: vscode.TextDocument): string {
    return path.basename(path.dirname(document.fileName));
}
