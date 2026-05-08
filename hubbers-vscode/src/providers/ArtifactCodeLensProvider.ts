import * as vscode from 'vscode';
import { detectArtifactType } from '../util/artifactScanner';

/** Document selectors for all Hubbers artifact manifest files. */
const ARTIFACT_SELECTORS: vscode.DocumentSelector = [
    { scheme: 'file', pattern: '**/agent.yaml' },
    { scheme: 'file', pattern: '**/tool.yaml' },
    { scheme: 'file', pattern: '**/pipeline.yaml' },
    { scheme: 'file', pattern: '**/SKILL.md' },
];

/**
 * Provides a "▶ Run" CodeLens at the very first non-empty line of every
 * Hubbers artifact manifest file.
 */
export class ArtifactCodeLensProvider implements vscode.CodeLensProvider {
    private readonly _onDidChangeCodeLenses = new vscode.EventEmitter<void>();
    readonly onDidChangeCodeLenses = this._onDidChangeCodeLenses.event;

    /** Returns the document selectors this provider handles. */
    static get documentSelector(): vscode.DocumentSelector {
        return ARTIFACT_SELECTORS;
    }

    provideCodeLenses(document: vscode.TextDocument): vscode.CodeLens[] {
        const artifactType = detectArtifactType(document);
        if (!artifactType) {
            return [];
        }

        // Place CodeLens above line 0 (the very first line of the file)
        const range = new vscode.Range(0, 0, 0, 0);

        const runLens = new vscode.CodeLens(range, {
            title: '▶ Run',
            command: 'hubbers.runCurrentArtifact',
            tooltip: `Run this ${artifactType} with the Hubbers CLI`,
        });

        return [runLens];
    }
}
