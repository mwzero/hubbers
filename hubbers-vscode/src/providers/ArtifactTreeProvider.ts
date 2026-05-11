import * as vscode from 'vscode';
import * as path from 'path';
import { ArtifactItem, ArtifactType, InputFile, TYPE_LABELS } from '../types';
import { scanWorkspaceArtifacts, scanInputFiles } from '../util/artifactScanner';

/** Ordered type display sequence in the sidebar. */
const TYPE_ORDER: ArtifactType[] = ['agent', 'tool', 'pipeline', 'skill'];

// ---------------------------------------------------------------------------
// Node types
// ---------------------------------------------------------------------------

/** Base class for all tree nodes. */
abstract class ArtifactNodeBase extends vscode.TreeItem {
    abstract readonly nodeType: 'category' | 'artifact';
}

/** Category header node (Agents / Tools / Pipelines / Skills). */
export class CategoryNode extends ArtifactNodeBase {
    readonly nodeType = 'category' as const;

    constructor(
        public readonly artifactType: ArtifactType,
        public readonly children: ArtifactNode[],
    ) {
        super(
            TYPE_LABELS[artifactType],
            children.length > 0
                ? vscode.TreeItemCollapsibleState.Expanded
                : vscode.TreeItemCollapsibleState.Collapsed,
        );
        this.description = `${children.length}`;
        this.contextValue = `category-${artifactType}`;
        this.iconPath = new vscode.ThemeIcon('folder');
    }
}

/** Leaf node representing a single Hubbers artifact. */
export class ArtifactNode extends ArtifactNodeBase {
    readonly nodeType = 'artifact' as const;

    constructor(public readonly item: ArtifactItem) {
        const hasInputs = scanInputFiles(item.filePath).length > 0;
        super(
            item.name,
            hasInputs
                ? vscode.TreeItemCollapsibleState.Collapsed
                : vscode.TreeItemCollapsibleState.None,
        );
        this.description = item.type;
        this.tooltip = item.filePath;
        this.contextValue = 'artifact';
        this.resourceUri = vscode.Uri.file(item.filePath);

        // Open the manifest file when clicking the node
        this.command = {
            command: 'vscode.open',
            title: 'Open',
            arguments: [vscode.Uri.file(item.filePath)],
        };

        this.iconPath = new vscode.ThemeIcon(iconForType(item.type));
    }

    /** Convenience accessor for commands that need the file URI. */
    get uri(): vscode.Uri {
        return vscode.Uri.file(this.item.filePath);
    }
}

/** Leaf node representing a pre-stored JSON input file for an artifact. */
export class InputFileNode extends vscode.TreeItem {
    readonly nodeType = 'inputFile' as const;

    constructor(
        public readonly inputFile: InputFile,
        public readonly parentItem: ArtifactItem,
    ) {
        super(inputFile.label, vscode.TreeItemCollapsibleState.None);
        this.description = 'input';
        this.tooltip = inputFile.filePath;
        this.contextValue = 'inputFile';
        this.resourceUri = vscode.Uri.file(inputFile.filePath);
        this.iconPath = new vscode.ThemeIcon('json');

        // Click opens the JSON file in the editor
        this.command = {
            command: 'vscode.open',
            title: 'Open',
            arguments: [vscode.Uri.file(inputFile.filePath)],
        };
    }
}

function iconForType(type: ArtifactType): string {
    switch (type) {
        case 'agent':    return 'hubot';
        case 'tool':     return 'tools';
        case 'pipeline': return 'git-merge';
        case 'skill':    return 'book';
    }
}

// ---------------------------------------------------------------------------
// Tree data provider
// ---------------------------------------------------------------------------

type TreeNode = CategoryNode | ArtifactNode | InputFileNode;

export class ArtifactTreeProvider implements vscode.TreeDataProvider<TreeNode> {
    private readonly _onDidChangeTreeData = new vscode.EventEmitter<TreeNode | undefined | void>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    private categories: CategoryNode[] = [];
    private readonly fileWatcher: vscode.FileSystemWatcher;
    private readonly inputWatcher: vscode.FileSystemWatcher;

    constructor() {
        // Watch for artifact manifest changes to trigger a refresh
        const watchPattern = '**/{agent.yaml,tool.yaml,pipeline.yaml,SKILL.md}';
        this.fileWatcher = vscode.workspace.createFileSystemWatcher(watchPattern);
        this.fileWatcher.onDidCreate(() => this.refresh());
        this.fileWatcher.onDidDelete(() => this.refresh());
        this.fileWatcher.onDidChange(() => this.refresh());

        // Watch for input file changes
        this.inputWatcher = vscode.workspace.createFileSystemWatcher('**/inputs/*.json');
        this.inputWatcher.onDidCreate(() => this.refresh());
        this.inputWatcher.onDidDelete(() => this.refresh());
    }

    /** Triggers a full re-scan of the workspace. */
    refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    dispose(): void {
        this.fileWatcher.dispose();
        this.inputWatcher.dispose();
        this._onDidChangeTreeData.dispose();
    }

    // -------------------------------------------------------------------------
    // TreeDataProvider interface
    // -------------------------------------------------------------------------

    getTreeItem(element: TreeNode): vscode.TreeItem {
        return element;
    }

    async getChildren(element?: TreeNode): Promise<TreeNode[]> {
        if (!element) {
            // Root: return category nodes
            this.categories = await this.buildCategories();
            return this.categories;
        }
        if (element instanceof CategoryNode) {
            return element.children;
        }
        if (element instanceof ArtifactNode) {
            return scanInputFiles(element.item.filePath).map(
                (f) => new InputFileNode(f, element.item),
            );
        }
        return [];
    }

    getParent(element: TreeNode): TreeNode | undefined {
        if (element instanceof InputFileNode) {
            const cat = this.categories.find((c) => c.artifactType === element.parentItem.type);
            return cat?.children.find((a) => a.item.filePath === element.parentItem.filePath);
        }
        if (element instanceof ArtifactNode) {
            return this.categories.find((c) => c.artifactType === element.item.type);
        }
        return undefined;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private async buildCategories(): Promise<CategoryNode[]> {
        const all = await scanWorkspaceArtifacts();

        const byType = new Map<ArtifactType, ArtifactNode[]>();
        for (const type of TYPE_ORDER) {
            byType.set(type, []);
        }
        for (const item of all) {
            byType.get(item.type)?.push(new ArtifactNode(item));
        }

        return TYPE_ORDER.map((type) => new CategoryNode(type, byType.get(type) ?? []));
    }
}
