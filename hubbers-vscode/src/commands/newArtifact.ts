import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { ArtifactType } from '../types';
import { getRepoPath } from '../util/config';

/** Sub-folder name for each artifact type (plural). */
const TYPE_FOLDERS: Record<ArtifactType, string> = {
    agent:    'agents',
    tool:     'tools',
    pipeline: 'pipelines',
    skill:    'skills',
};

/** Manifest file name for each artifact type. */
const TYPE_FILES: Record<ArtifactType, string> = {
    agent:    'agent.yaml',
    tool:     'tool.yaml',
    pipeline: 'pipeline.yaml',
    skill:    'SKILL.md',
};

/** Minimal valid manifest content for each artifact type. */
function manifestTemplate(type: ArtifactType, name: string): string {
    switch (type) {
        case 'agent':
            return [
                `agent:`,
                `  name: ${name}`,
                `  version: 1.0.0`,
                `  description: ""`,
                ``,
                `model:`,
                `  provider: openai`,
                `  name: gpt-4o`,
                ``,
                `instructions: |`,
                `  You are a helpful assistant.`,
                ``,
            ].join('\n');

        case 'tool':
            return [
                `tool:`,
                `  name: ${name}`,
                `  version: 1.0.0`,
                `  description: ""`,
                ``,
                `type: http`,
                ``,
            ].join('\n');

        case 'pipeline':
            return [
                `pipeline:`,
                `  name: ${name}`,
                `  version: 1.0.0`,
                `  description: ""`,
                ``,
                `steps: []`,
                ``,
            ].join('\n');

        case 'skill':
            return [
                `# ${name}`,
                ``,
                `Describe the skill here.`,
                ``,
            ].join('\n');
    }
}

/**
 * Command: Hubbers: New Artifact
 *
 * Guides the user through creating a new Hubbers artifact scaffold:
 *  1. Choose type (agent / tool / pipeline / skill)
 *  2. Enter a dot-separated name
 *  3. Writes the manifest into <repoRoot>/<type>s/<name>/<manifest>
 *  4. Opens the newly created file
 */
export async function newArtifact(refresh: () => void): Promise<void> {
    // Step 1 — artifact type
    const typeItems: vscode.QuickPickItem[] = [
        { label: '$(hubot) Agent',        description: 'agent.yaml',    detail: 'AI agent with model, instructions, and tool access' },
        { label: '$(tools) Tool',          description: 'tool.yaml',     detail: 'Reusable capability (http, file, script, …)' },
        { label: '$(git-merge) Pipeline',  description: 'pipeline.yaml', detail: 'Multi-step workflow composing tools and agents' },
        { label: '$(book) Skill',          description: 'SKILL.md',      detail: 'Prompt-engineering skill document' },
    ];

    const typePick = await vscode.window.showQuickPick(typeItems, {
        title: 'New Hubbers Artifact — Step 1 of 2',
        placeHolder: 'Select artifact type',
    });
    if (!typePick) { return; }

    const typeMap: Record<string, ArtifactType> = {
        '$(hubot) Agent':        'agent',
        '$(tools) Tool':         'tool',
        '$(git-merge) Pipeline': 'pipeline',
        '$(book) Skill':         'skill',
    };
    const artifactType = typeMap[typePick.label];
    await newArtifactOfType(artifactType, refresh);
}

/**
 * Creates a new artifact of a specific type, skipping the type-picker step.
 * Used by the per-type commands (hubbers.newAgent, hubbers.newTool, …).
 */
export async function newArtifactOfType(artifactType: ArtifactType, refresh: () => void): Promise<void> {
    const namePattern = /^[a-z][a-z0-9.\-]*$/;
    const artifactName = await vscode.window.showInputBox({
        title: 'New Hubbers Artifact — Step 2 of 2',
        prompt: `Enter a dot-separated name for the ${artifactType}`,
        placeHolder: artifactType === 'agent' ? 'universal.task' :
                     artifactType === 'tool'  ? 'rss.fetch' :
                     artifactType === 'pipeline' ? 'rss.sentiment.csv' : 'my.skill',
        validateInput: (v) => {
            if (!v.trim()) { return 'Name cannot be empty'; }
            if (!namePattern.test(v.trim())) {
                return 'Use lowercase letters, digits, dots and hyphens (e.g. rss.fetch)';
            }
            return undefined;
        },
    });
    if (!artifactName) { return; }

    // Determine base directory
    const repoPath = getRepoPath();
    const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
    const baseDir = repoPath ?? workspaceRoot;
    if (!baseDir) {
        vscode.window.showErrorMessage('No workspace folder is open and hubbers.repoPath is not configured.');
        return;
    }

    const artifactDir = path.join(baseDir, TYPE_FOLDERS[artifactType], artifactName.trim());
    const manifestPath = path.join(artifactDir, TYPE_FILES[artifactType]);

    if (fs.existsSync(manifestPath)) {
        const choice = await vscode.window.showWarningMessage(
            `Artifact "${artifactName.trim()}" already exists. Open its manifest?`,
            'Open', 'Cancel',
        );
        if (choice === 'Open') {
            vscode.window.showTextDocument(vscode.Uri.file(manifestPath));
        }
        return;
    }

    try {
        fs.mkdirSync(artifactDir, { recursive: true });
        fs.writeFileSync(manifestPath, manifestTemplate(artifactType, artifactName.trim()), 'utf8');
    } catch (err) {
        vscode.window.showErrorMessage(
            `Failed to create artifact: ${err instanceof Error ? err.message : String(err)}`,
        );
        return;
    }

    refresh();
    vscode.window.showTextDocument(vscode.Uri.file(manifestPath));
}
