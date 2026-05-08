import * as vscode from 'vscode';

/** Returns the extension's configuration section. */
function cfg() {
    return vscode.workspace.getConfiguration('hubbers');
}

/**
 * Returns the configured jar path or executable name.
 * Falls back to 'hubbers' (expects it on PATH) when not explicitly set.
 */
export function getJarPath(): string {
    return cfg().get<string>('jarPath', '').trim() || 'hubbers';
}

/**
 * Returns the configured artifact repository root, or undefined to use
 * the workspace root.
 */
export function getRepoPath(): string | undefined {
    const v = cfg().get<string>('repoPath', '').trim();
    return v.length > 0 ? v : undefined;
}

/**
 * Returns true when jarPath is explicitly a .jar file path, meaning we
 * should invoke `java -jar <jarPath> ...` rather than `<jarPath> ...`.
 */
export function isJarFile(jarPath: string): boolean {
    return jarPath.toLowerCase().endsWith('.jar');
}

/**
 * Builds the command array for spawning the Hubbers CLI.
 * Examples:
 *   jarPath='/path/to/hubbers.jar' → ['java', '-jar', '/path/to/hubbers.jar', ...args]
 *   jarPath='hubbers'              → ['hubbers', ...args]
 */
export function buildCommand(args: string[]): { cmd: string; cmdArgs: string[] } {
    const jarPath = getJarPath();
    if (isJarFile(jarPath)) {
        return { cmd: 'java', cmdArgs: ['-jar', jarPath, ...args] };
    }
    return { cmd: jarPath, cmdArgs: args };
}
