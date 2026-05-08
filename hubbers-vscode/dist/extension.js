/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ([
/* 0 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(__webpack_require__(1));
const ArtifactTreeProvider_1 = __webpack_require__(2);
const ArtifactCodeLensProvider_1 = __webpack_require__(8);
const McpServerProvider_1 = __webpack_require__(9);
const configureMcp_1 = __webpack_require__(10);
const runArtifact_1 = __webpack_require__(11);
const importArtifacts_1 = __webpack_require__(14);
const schemaRegistrar_1 = __webpack_require__(15);
const processRunner_1 = __webpack_require__(12);
function activate(context) {
    // -------------------------------------------------------------------------
    // Artifact tree view (sidebar)
    // -------------------------------------------------------------------------
    const treeProvider = new ArtifactTreeProvider_1.ArtifactTreeProvider();
    const treeView = vscode.window.createTreeView('hubbersArtifacts', {
        treeDataProvider: treeProvider,
        showCollapseAll: true,
    });
    context.subscriptions.push(treeView, treeProvider);
    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------
    context.subscriptions.push(vscode.commands.registerCommand('hubbers.refreshArtifacts', () => treeProvider.refresh()), vscode.commands.registerCommand('hubbers.runArtifact', (node) => (0, runArtifact_1.runArtifact)(node)), 
    // Called from CodeLens and command palette — no node argument
    vscode.commands.registerCommand('hubbers.runCurrentArtifact', () => (0, runArtifact_1.runArtifact)(undefined)), vscode.commands.registerCommand('hubbers.openArtifact', (node) => {
        if (node?.uri) {
            vscode.window.showTextDocument(node.uri);
        }
    }), vscode.commands.registerCommand('hubbers.configureMcp', () => (0, configureMcp_1.configureMcp)()), vscode.commands.registerCommand('hubbers.importArtifacts', (uri) => (0, importArtifacts_1.importArtifacts)(() => treeProvider.refresh(), uri)));
    // -------------------------------------------------------------------------
    // CodeLens — "▶ Run" above artifact manifests
    // -------------------------------------------------------------------------
    context.subscriptions.push(vscode.languages.registerCodeLensProvider(ArtifactCodeLensProvider_1.ArtifactCodeLensProvider.documentSelector, new ArtifactCodeLensProvider_1.ArtifactCodeLensProvider()));
    // -------------------------------------------------------------------------
    // YAML schema registration (requires redhat.vscode-yaml when installed)
    // -------------------------------------------------------------------------
    (0, schemaRegistrar_1.registerSchemas)(context);
    // -------------------------------------------------------------------------
    // MCP server check — notify user if not yet configured
    // -------------------------------------------------------------------------
    new McpServerProvider_1.McpServerProvider().checkAndNotify();
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
    context.subscriptions.push((0, processRunner_1.getOutputChannel)());
}
function deactivate() {
    // No explicit teardown needed — VS Code disposes context.subscriptions automatically.
}


/***/ }),
/* 1 */
/***/ ((module) => {

module.exports = require("vscode");

/***/ }),
/* 2 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.ArtifactTreeProvider = exports.ArtifactNode = exports.CategoryNode = void 0;
const vscode = __importStar(__webpack_require__(1));
const types_1 = __webpack_require__(3);
const artifactScanner_1 = __webpack_require__(4);
/** Ordered type display sequence in the sidebar. */
const TYPE_ORDER = ['agent', 'tool', 'pipeline', 'skill'];
// ---------------------------------------------------------------------------
// Node types
// ---------------------------------------------------------------------------
/** Base class for all tree nodes. */
class ArtifactNodeBase extends vscode.TreeItem {
}
/** Category header node (Agents / Tools / Pipelines / Skills). */
class CategoryNode extends ArtifactNodeBase {
    artifactType;
    children;
    nodeType = 'category';
    constructor(artifactType, children) {
        super(types_1.TYPE_LABELS[artifactType], children.length > 0
            ? vscode.TreeItemCollapsibleState.Expanded
            : vscode.TreeItemCollapsibleState.Collapsed);
        this.artifactType = artifactType;
        this.children = children;
        this.description = `${children.length}`;
        this.contextValue = 'category';
        this.iconPath = new vscode.ThemeIcon('folder');
    }
}
exports.CategoryNode = CategoryNode;
/** Leaf node representing a single Hubbers artifact. */
class ArtifactNode extends ArtifactNodeBase {
    item;
    nodeType = 'artifact';
    constructor(item) {
        super(item.name, vscode.TreeItemCollapsibleState.None);
        this.item = item;
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
    get uri() {
        return vscode.Uri.file(this.item.filePath);
    }
}
exports.ArtifactNode = ArtifactNode;
function iconForType(type) {
    switch (type) {
        case 'agent': return 'hubot';
        case 'tool': return 'tools';
        case 'pipeline': return 'git-merge';
        case 'skill': return 'book';
    }
}
class ArtifactTreeProvider {
    _onDidChangeTreeData = new vscode.EventEmitter();
    onDidChangeTreeData = this._onDidChangeTreeData.event;
    categories = [];
    fileWatcher;
    constructor() {
        // Watch for artifact manifest changes to trigger a refresh
        const watchPattern = '**/{agent.yaml,tool.yaml,pipeline.yaml,SKILL.md}';
        this.fileWatcher = vscode.workspace.createFileSystemWatcher(watchPattern);
        this.fileWatcher.onDidCreate(() => this.refresh());
        this.fileWatcher.onDidDelete(() => this.refresh());
        this.fileWatcher.onDidChange(() => this.refresh());
    }
    /** Triggers a full re-scan of the workspace. */
    refresh() {
        this._onDidChangeTreeData.fire();
    }
    dispose() {
        this.fileWatcher.dispose();
        this._onDidChangeTreeData.dispose();
    }
    // -------------------------------------------------------------------------
    // TreeDataProvider interface
    // -------------------------------------------------------------------------
    getTreeItem(element) {
        return element;
    }
    async getChildren(element) {
        if (!element) {
            // Root: return category nodes
            this.categories = await this.buildCategories();
            return this.categories;
        }
        if (element instanceof CategoryNode) {
            return element.children;
        }
        return [];
    }
    getParent(element) {
        if (element instanceof ArtifactNode) {
            return this.categories.find((c) => c.artifactType === element.item.type);
        }
        return undefined;
    }
    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------
    async buildCategories() {
        const all = await (0, artifactScanner_1.scanWorkspaceArtifacts)();
        const byType = new Map();
        for (const type of TYPE_ORDER) {
            byType.set(type, []);
        }
        for (const item of all) {
            byType.get(item.type)?.push(new ArtifactNode(item));
        }
        return TYPE_ORDER.map((type) => new CategoryNode(type, byType.get(type) ?? []));
    }
}
exports.ArtifactTreeProvider = ArtifactTreeProvider;


/***/ }),
/* 3 */
/***/ ((__unused_webpack_module, exports) => {


Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.TYPE_COMMANDS = exports.TYPE_LABELS = void 0;
/** Display labels for each artifact type category. */
exports.TYPE_LABELS = {
    agent: 'Agents',
    tool: 'Tools',
    pipeline: 'Pipelines',
    skill: 'Skills',
};
/** CLI sub-command word for each artifact type. */
exports.TYPE_COMMANDS = {
    agent: 'agent',
    tool: 'tool',
    pipeline: 'pipeline',
    skill: 'skill',
};


/***/ }),
/* 4 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.scanWorkspaceArtifacts = scanWorkspaceArtifacts;
exports.scanInputFiles = scanInputFiles;
exports.detectArtifactType = detectArtifactType;
exports.detectArtifactName = detectArtifactName;
const vscode = __importStar(__webpack_require__(1));
const path = __importStar(__webpack_require__(5));
const fs = __importStar(__webpack_require__(6));
const config_1 = __webpack_require__(7);
/** File name for each artifact type. */
const ARTIFACT_FILES = {
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
async function scanWorkspaceArtifacts() {
    const repoPath = (0, config_1.getRepoPath)();
    const results = [];
    const artifactTypes = ['agent', 'tool', 'pipeline', 'skill'];
    await Promise.all(artifactTypes.map(async (type) => {
        const fileName = ARTIFACT_FILES[type];
        const pattern = repoPath
            ? new vscode.RelativePattern(repoPath, `**/${fileName}`)
            : `**/${fileName}`;
        const uris = await vscode.workspace.findFiles(pattern, EXCLUDE_PATTERNS);
        for (const uri of uris) {
            const name = extractArtifactName(uri.fsPath, fileName);
            results.push({ name, type, filePath: uri.fsPath });
        }
    }));
    return results.sort((a, b) => a.type.localeCompare(b.type) || a.name.localeCompare(b.name));
}
/**
 * Derives the artifact name from its manifest file path.
 * Convention: the parent directory of the manifest file is the artifact name.
 * e.g., '/repo/agents/universal.task/agent.yaml' → 'universal.task'
 */
function extractArtifactName(filePath, _fileName) {
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
function scanInputFiles(manifestFilePath) {
    const inputsDir = path.join(path.dirname(manifestFilePath), 'inputs');
    if (!fs.existsSync(inputsDir)) {
        return [];
    }
    let entries;
    try {
        entries = fs.readdirSync(inputsDir, { withFileTypes: true });
    }
    catch {
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
function detectArtifactType(document) {
    const fileName = path.basename(document.fileName);
    for (const [type, manifestFile] of Object.entries(ARTIFACT_FILES)) {
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
function detectArtifactName(document) {
    return path.basename(path.dirname(document.fileName));
}


/***/ }),
/* 5 */
/***/ ((module) => {

module.exports = require("path");

/***/ }),
/* 6 */
/***/ ((module) => {

module.exports = require("fs");

/***/ }),
/* 7 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.getJarPath = getJarPath;
exports.getRepoPath = getRepoPath;
exports.isJarFile = isJarFile;
exports.buildCommand = buildCommand;
const vscode = __importStar(__webpack_require__(1));
/** Returns the extension's configuration section. */
function cfg() {
    return vscode.workspace.getConfiguration('hubbers');
}
/**
 * Returns the configured jar path or executable name.
 * Falls back to 'hubbers' (expects it on PATH) when not explicitly set.
 */
function getJarPath() {
    return cfg().get('jarPath', '').trim() || 'hubbers';
}
/**
 * Returns the configured artifact repository root, or undefined to use
 * the workspace root.
 */
function getRepoPath() {
    const v = cfg().get('repoPath', '').trim();
    return v.length > 0 ? v : undefined;
}
/**
 * Returns true when jarPath is explicitly a .jar file path, meaning we
 * should invoke `java -jar <jarPath> ...` rather than `<jarPath> ...`.
 */
function isJarFile(jarPath) {
    return jarPath.toLowerCase().endsWith('.jar');
}
/**
 * Builds the command array for spawning the Hubbers CLI.
 * Examples:
 *   jarPath='/path/to/hubbers.jar' → ['java', '-jar', '/path/to/hubbers.jar', ...args]
 *   jarPath='hubbers'              → ['hubbers', ...args]
 */
function buildCommand(args) {
    const jarPath = getJarPath();
    if (isJarFile(jarPath)) {
        return { cmd: 'java', cmdArgs: ['-jar', jarPath, ...args] };
    }
    return { cmd: jarPath, cmdArgs: args };
}


/***/ }),
/* 8 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.ArtifactCodeLensProvider = void 0;
const vscode = __importStar(__webpack_require__(1));
const artifactScanner_1 = __webpack_require__(4);
/** Document selectors for all Hubbers artifact manifest files. */
const ARTIFACT_SELECTORS = [
    { scheme: 'file', pattern: '**/agent.yaml' },
    { scheme: 'file', pattern: '**/tool.yaml' },
    { scheme: 'file', pattern: '**/pipeline.yaml' },
    { scheme: 'file', pattern: '**/SKILL.md' },
];
/**
 * Provides a "▶ Run" CodeLens at the very first non-empty line of every
 * Hubbers artifact manifest file.
 */
class ArtifactCodeLensProvider {
    _onDidChangeCodeLenses = new vscode.EventEmitter();
    onDidChangeCodeLenses = this._onDidChangeCodeLenses.event;
    /** Returns the document selectors this provider handles. */
    static get documentSelector() {
        return ARTIFACT_SELECTORS;
    }
    provideCodeLenses(document) {
        const artifactType = (0, artifactScanner_1.detectArtifactType)(document);
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
exports.ArtifactCodeLensProvider = ArtifactCodeLensProvider;


/***/ }),
/* 9 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.McpServerProvider = void 0;
const vscode = __importStar(__webpack_require__(1));
const path = __importStar(__webpack_require__(5));
const fs = __importStar(__webpack_require__(6));
const MCP_CONFIG_PATH = '.vscode/mcp.json';
const SERVER_KEY = 'hubbers';
/**
 * Checks whether the Hubbers MCP server is already configured in the
 * workspace's .vscode/mcp.json file and notifies the user if not.
 */
class McpServerProvider {
    checkAndNotify() {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) {
            return;
        }
        const mcpFilePath = path.join(workspaceFolder.uri.fsPath, MCP_CONFIG_PATH);
        if (this.isMcpConfigured(mcpFilePath)) {
            return;
        }
        vscode.window
            .showInformationMessage('Hubbers: MCP server is not configured. Register it to use Hubbers tools in GitHub Copilot Chat.', 'Configure Now', 'Remind Me Later')
            .then((choice) => {
            if (choice === 'Configure Now') {
                vscode.commands.executeCommand('hubbers.configureMcp');
            }
        });
    }
    /** Returns true when .vscode/mcp.json already contains a 'hubbers' server entry. */
    isMcpConfigured(mcpFilePath) {
        if (!fs.existsSync(mcpFilePath)) {
            return false;
        }
        try {
            const config = JSON.parse(fs.readFileSync(mcpFilePath, 'utf8'));
            return SERVER_KEY in (config?.servers ?? {});
        }
        catch {
            return false;
        }
    }
}
exports.McpServerProvider = McpServerProvider;


/***/ }),
/* 10 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.configureMcp = configureMcp;
const vscode = __importStar(__webpack_require__(1));
const path = __importStar(__webpack_require__(5));
const fs = __importStar(__webpack_require__(6));
const config_1 = __webpack_require__(7);
const MCP_CONFIG_PATH = '.vscode/mcp.json';
const SERVER_KEY = 'hubbers';
/**
 * Builds the MCP server entry based on the current jarPath and repoPath.
 * If jarPath ends with .jar, uses 'java -jar <path> mcp'.
 * Otherwise uses the executable directly (e.g., 'hubbers mcp' from PATH).
 * When repoPath is provided it is set as the working directory for the server.
 */
function buildMcpEntry(jarPath, repoPath) {
    const entry = (0, config_1.isJarFile)(jarPath)
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
async function configureMcp() {
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        vscode.window.showErrorMessage('No workspace folder is open. Open a folder first.');
        return;
    }
    let jarPath = (0, config_1.getJarPath)();
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
    let repoPath = (0, config_1.getRepoPath)();
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
    const result = await vscode.window.showInformationMessage(`Hubbers MCP server registered in ${MCP_CONFIG_PATH}. Reload VS Code to activate it.`, 'Reload Window', 'Open mcp.json');
    if (result === 'Reload Window') {
        vscode.commands.executeCommand('workbench.action.reloadWindow');
    }
    else if (result === 'Open mcp.json') {
        vscode.window.showTextDocument(vscode.Uri.file(mcpFilePath));
    }
}
/** Creates or updates .vscode/mcp.json preserving any existing servers. */
async function writeMcpConfig(mcpFilePath, entry) {
    const vscodeDir = path.dirname(mcpFilePath);
    if (!fs.existsSync(vscodeDir)) {
        fs.mkdirSync(vscodeDir, { recursive: true });
    }
    let config = { servers: {} };
    if (fs.existsSync(mcpFilePath)) {
        try {
            const raw = fs.readFileSync(mcpFilePath, 'utf8');
            config = JSON.parse(raw);
            config.servers ??= {};
        }
        catch {
            // Corrupted config — overwrite it
        }
    }
    config.servers[SERVER_KEY] = entry;
    fs.writeFileSync(mcpFilePath, JSON.stringify(config, null, 2) + '\n', 'utf8');
}
/** Prompts the user for the artifact repository root path. Returns undefined if cancelled. */
async function promptForRepoPath() {
    const choice = await vscode.window.showInformationMessage('Where is your Hubbers artifact repository (agents/, tools/, pipelines/, skills/)?', { modal: true }, 'Browse for folder', 'Enter path manually', 'Use workspace root');
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
async function promptForJarPath() {
    const choice = await vscode.window.showInformationMessage("Hubbers couldn't find the CLI on PATH. How would you like to configure it?", { modal: true }, 'Browse for hubbers.jar', 'Enter path manually', 'Use "hubbers" from PATH');
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


/***/ }),
/* 11 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.runArtifact = runArtifact;
const vscode = __importStar(__webpack_require__(1));
const fs = __importStar(__webpack_require__(6));
const ArtifactTreeProvider_1 = __webpack_require__(2);
const artifactScanner_1 = __webpack_require__(4);
const processRunner_1 = __webpack_require__(12);
const types_1 = __webpack_require__(3);
/**
 * Command: Hubbers: Run Artifact
 *
 * Can be triggered from:
 * 1. Tree view context menu  → receives an ArtifactNode argument
 * 2. CodeLens "▶ Run"       → receives no argument (uses active editor)
 * 3. Command palette         → no argument (uses active editor)
 */
async function runArtifact(node) {
    let artifactType;
    let artifactName;
    let manifestFilePath;
    if (node instanceof ArtifactTreeProvider_1.ArtifactNode) {
        // Called from tree view — node carries full artifact info
        artifactType = node.item.type;
        artifactName = node.item.name;
        manifestFilePath = node.item.filePath;
    }
    else {
        // Called from CodeLens or command palette — infer from active editor
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showErrorMessage('No file is open. Open an agent.yaml, tool.yaml, pipeline.yaml, or SKILL.md first.');
            return;
        }
        artifactType = (0, artifactScanner_1.detectArtifactType)(editor.document);
        artifactName = (0, artifactScanner_1.detectArtifactName)(editor.document);
        manifestFilePath = editor.document.uri.fsPath;
    }
    if (!artifactType || !artifactName) {
        vscode.window.showErrorMessage('Could not determine artifact type. Make sure the active file is a Hubbers manifest.');
        return;
    }
    // Resolve the JSON input to pass to the CLI
    const inputJson = await resolveInput(artifactType, artifactName, manifestFilePath);
    if (inputJson === undefined) {
        return; // User cancelled
    }
    const subCommand = types_1.TYPE_COMMANDS[artifactType];
    const args = [subCommand, 'run', artifactName];
    try {
        await (0, processRunner_1.runHubbersCommand)(args, inputJson.trim() || undefined);
    }
    catch (err) {
        if (err instanceof processRunner_1.HubbersNotFoundError) {
            const choice = await vscode.window.showErrorMessage(err.message, 'Configure hubbers.jarPath', 'Show Output');
            if (choice === 'Configure hubbers.jarPath') {
                vscode.commands.executeCommand('workbench.action.openSettings', 'hubbers.jarPath');
            }
            else if (choice === 'Show Output') {
                (0, processRunner_1.getOutputChannel)().show();
            }
        }
        else {
            const message = err instanceof Error ? err.message : String(err);
            vscode.window
                .showErrorMessage(`Hubbers run failed: ${message}`, 'Show Output')
                .then((choice) => {
                if (choice === 'Show Output') {
                    (0, processRunner_1.getOutputChannel)().show();
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
async function resolveInput(artifactType, artifactName, manifestFilePath) {
    const inputFiles = manifestFilePath ? (0, artifactScanner_1.scanInputFiles)(manifestFilePath) : [];
    // No pre-stored inputs → fall back to inline box (original behaviour)
    if (inputFiles.length === 0) {
        return promptInline(artifactType, artifactName);
    }
    const fileItems = inputFiles.map((f) => ({
        label: `$(file-code) ${f.label}`,
        description: 'input file',
        filePath: f.filePath,
    }));
    const actionItems = [
        { label: '', kind: vscode.QuickPickItemKind.Separator },
        { label: '$(pencil) Type JSON inline', action: 'inline' },
        { label: '$(folder-opened) Browse for JSON file\u2026', action: 'browse' },
        { label: '$(circle-slash) No input', action: 'none' },
    ];
    const picked = await vscode.window.showQuickPick([...fileItems, ...actionItems], {
        title: `Input for ${artifactType} "${artifactName}"`,
        placeHolder: 'Select an input file or choose an option',
    });
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
async function promptInline(artifactType, artifactName) {
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
            }
            catch {
                return 'Invalid JSON';
            }
        },
    });
}
/**
 * Reads a JSON file from disk and validates its content.
 * Shows an error message and returns undefined if reading or parsing fails.
 */
function readAndValidateJsonFile(filePath) {
    let raw;
    try {
        raw = fs.readFileSync(filePath, 'utf8');
    }
    catch (err) {
        vscode.window.showErrorMessage(`Failed to read input file: ${filePath}`);
        return undefined;
    }
    try {
        JSON.parse(raw);
    }
    catch {
        vscode.window.showErrorMessage(`Input file contains invalid JSON: ${filePath}`);
        return undefined;
    }
    return raw;
}


/***/ }),
/* 12 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.HubbersNotFoundError = void 0;
exports.getOutputChannel = getOutputChannel;
exports.runHubbersCommand = runHubbersCommand;
const vscode = __importStar(__webpack_require__(1));
const cp = __importStar(__webpack_require__(13));
const config_1 = __webpack_require__(7);
/** Thrown when the Hubbers executable cannot be found (ENOENT). */
class HubbersNotFoundError extends Error {
    constructor(cmd) {
        super(`Hubbers executable not found: "${cmd}".\n` +
            `Configure the path via Settings → hubbers.jarPath, or install the Hubbers CLI on PATH.`);
        this.name = 'HubbersNotFoundError';
    }
}
exports.HubbersNotFoundError = HubbersNotFoundError;
let outputChannel;
/** Returns the shared Hubbers output channel, creating it on first use. */
function getOutputChannel() {
    if (!outputChannel) {
        outputChannel = vscode.window.createOutputChannel('Hubbers');
    }
    return outputChannel;
}
/**
 * Runs a Hubbers CLI command and streams its output to the Hubbers output channel.
 *
 * @param args  CLI arguments after the binary/jar path (e.g., ['agent', 'run', 'my.agent'])
 * @param inputJson  Optional JSON string passed via --input flag
 * @returns A Promise that resolves with the full stdout output, or rejects on non-zero exit.
 */
function runHubbersCommand(args, inputJson) {
    const channel = getOutputChannel();
    channel.show(true);
    const allArgs = inputJson ? [...args, '--input', inputJson] : args;
    const { cmd, cmdArgs } = (0, config_1.buildCommand)(allArgs);
    const repoPath = (0, config_1.getRepoPath)();
    const spawnOptions = {
        cwd: repoPath ?? vscode.workspace.workspaceFolders?.[0]?.uri.fsPath,
        env: { ...process.env },
        shell: false,
    };
    channel.appendLine(`\n▶ ${cmd} ${cmdArgs.join(' ')}`);
    channel.appendLine('─'.repeat(60));
    return new Promise((resolve, reject) => {
        const child = cp.spawn(cmd, cmdArgs, spawnOptions);
        const chunks = [];
        child.stdout?.on('data', (data) => {
            const text = data.toString();
            chunks.push(text);
            channel.append(text);
        });
        child.stderr?.on('data', (data) => {
            // Write stderr in a distinct prefix so it's easy to spot
            channel.append('[stderr] ' + data.toString());
        });
        child.on('error', (err) => {
            if (err.code === 'ENOENT') {
                const notFound = new HubbersNotFoundError(cmd);
                channel.appendLine(notFound.message);
                reject(notFound);
            }
            else {
                const msg = `Failed to start Hubbers: ${err.message}`;
                channel.appendLine(msg);
                reject(new Error(msg));
            }
        });
        child.on('close', (code) => {
            channel.appendLine('─'.repeat(60));
            if (code === 0) {
                resolve(chunks.join(''));
            }
            else {
                const msg = `Hubbers exited with code ${code}`;
                channel.appendLine(msg);
                reject(new Error(msg));
            }
        });
    });
}


/***/ }),
/* 13 */
/***/ ((module) => {

module.exports = require("child_process");

/***/ }),
/* 14 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.importArtifacts = importArtifacts;
const vscode = __importStar(__webpack_require__(1));
const path = __importStar(__webpack_require__(5));
const fs = __importStar(__webpack_require__(6));
const types_1 = __webpack_require__(3);
const config_1 = __webpack_require__(7);
/** Manifest file name per type. */
const MANIFEST_FILES = {
    agent: 'agent.yaml',
    tool: 'tool.yaml',
    pipeline: 'pipeline.yaml',
    skill: 'SKILL.md',
};
/** Destination subfolder inside the repo root per type. */
const DEST_FOLDERS = {
    agent: 'agents',
    tool: 'tools',
    pipeline: 'pipelines',
    skill: 'skills',
};
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
async function importArtifacts(treeRefresh, sourceUri) {
    // -------------------------------------------------------------------------
    // 1. Resolve source folder
    // -------------------------------------------------------------------------
    let sourceDir;
    if (sourceUri?.fsPath) {
        // Called from Explorer context menu on a folder
        sourceDir = sourceUri.fsPath;
    }
    else {
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
        vscode.window.showInformationMessage(`No Hubbers artifacts found in "${sourceDir}". ` +
            'Expected agent.yaml, tool.yaml, pipeline.yaml, or SKILL.md files.');
        return;
    }
    // -------------------------------------------------------------------------
    // 3. Multi-select QuickPick
    // -------------------------------------------------------------------------
    const items = found.map((a) => ({
        label: a.name,
        description: types_1.TYPE_LABELS[a.type],
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
    let repoRoot = (0, config_1.getRepoPath)() ?? vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
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
        const save = await vscode.window.showInformationMessage(`Save "${repoRoot}" as hubbers.repoPath?`, saveLabel, 'No, use once');
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
            const choice = await vscode.window.showWarningMessage(`"${artifact.name}" (${types_1.TYPE_LABELS[artifact.type]}) already exists in the repo.`, { modal: false }, 'Overwrite', 'Skip');
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
    const parts = [];
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
async function scanFolderForArtifacts(root) {
    const results = [];
    walkDir(root, results);
    return results;
}
/** Synchronous depth-first directory walk. */
function walkDir(dir, results) {
    let entries;
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    }
    catch {
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
        }
        else if (entry.isFile()) {
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
function typeFromFileName(fileName) {
    switch (fileName) {
        case 'agent.yaml': return 'agent';
        case 'tool.yaml': return 'tool';
        case 'pipeline.yaml': return 'pipeline';
        case 'SKILL.md': return 'skill';
        default: return undefined;
    }
}
/** Recursively copies srcDir into destDir (destDir must not exist yet). */
function copyDirRecursive(srcDir, destDir) {
    fs.mkdirSync(destDir, { recursive: true });
    const entries = fs.readdirSync(srcDir, { withFileTypes: true });
    for (const entry of entries) {
        const src = path.join(srcDir, entry.name);
        const dest = path.join(destDir, entry.name);
        if (entry.isDirectory()) {
            copyDirRecursive(src, dest);
        }
        else {
            fs.copyFileSync(src, dest);
        }
    }
}


/***/ }),
/* 15 */
/***/ (function(__unused_webpack_module, exports, __webpack_require__) {


var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", ({ value: true }));
exports.registerSchemas = registerSchemas;
const vscode = __importStar(__webpack_require__(1));
const path = __importStar(__webpack_require__(5));
/** Schema file associations: artifact file name → schema file name inside /schemas/. */
const SCHEMA_ASSOCIATIONS = [
    { filePattern: 'agent.yaml', schemaFile: 'agent.schema.json' },
    { filePattern: 'tool.yaml', schemaFile: 'tool.schema.json' },
    { filePattern: 'pipeline.yaml', schemaFile: 'pipeline.schema.json' },
];
/**
 * Registers Hubbers schemas with the redhat.vscode-yaml extension (if installed).
 * The yamlValidation contribution in package.json handles the static case;
 * this function provides the programmatic path for custom URI handling.
 */
function registerSchemas(context) {
    const yamlExt = vscode.extensions.getExtension('redhat.vscode-yaml');
    if (!yamlExt) {
        // Fallback: package.json yamlValidation contribution already covers basic cases.
        return;
    }
    yamlExt.activate().then((api) => {
        if (!api?.registerContributor) {
            return;
        }
        api.registerContributor('hubbers', (resource) => {
            const fileName = path.basename(resource);
            const assoc = SCHEMA_ASSOCIATIONS.find((a) => a.filePattern === fileName);
            if (!assoc) {
                return undefined;
            }
            return vscode.Uri.joinPath(context.extensionUri, 'schemas', assoc.schemaFile).toString();
        }, (_schemaUri) => {
            // Schema content is served from the URI returned above; no custom content needed.
            return undefined;
        });
    });
}


/***/ })
/******/ 	]);
/************************************************************************/
/******/ 	// The module cache
/******/ 	var __webpack_module_cache__ = {};
/******/ 	
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/ 		// Check if module is in cache
/******/ 		var cachedModule = __webpack_module_cache__[moduleId];
/******/ 		if (cachedModule !== undefined) {
/******/ 			return cachedModule.exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = __webpack_module_cache__[moduleId] = {
/******/ 			// no module.id needed
/******/ 			// no module.loaded needed
/******/ 			exports: {}
/******/ 		};
/******/ 	
/******/ 		// Execute the module function
/******/ 		__webpack_modules__[moduleId].call(module.exports, module, module.exports, __webpack_require__);
/******/ 	
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	// This entry module is referenced by other modules so it can't be inlined
/******/ 	var __webpack_exports__ = __webpack_require__(0);
/******/ 	module.exports = __webpack_exports__;
/******/ 	
/******/ })()
;
//# sourceMappingURL=extension.js.map