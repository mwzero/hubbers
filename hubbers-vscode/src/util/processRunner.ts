import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { buildCommand, getRepoPath } from './config';

/** Thrown when the Hubbers executable cannot be found (ENOENT). */
export class HubbersNotFoundError extends Error {
    constructor(cmd: string) {
        super(
            `Hubbers executable not found: "${cmd}".\n` +
            `Configure the path via Settings → hubbers.jarPath, or install the Hubbers CLI on PATH.`,
        );
        this.name = 'HubbersNotFoundError';
    }
}

let outputChannel: vscode.OutputChannel | undefined;

/** Returns the shared Hubbers output channel, creating it on first use. */
export function getOutputChannel(): vscode.OutputChannel {
    if (!outputChannel) {
        outputChannel = vscode.window.createOutputChannel('Hubbers');
    }
    return outputChannel;
}

/**
 * Runs a Hubbers CLI command in a dedicated integrated terminal.
 *
 * Using a terminal (real TTY) allows the CLI to display interactive prompts
 * that the user can respond to directly. The same named terminal is reused
 * across runs; a new one is created when the previous one has exited.
 *
 * JSON quoting strategy:
 * - When a file path is provided, it is passed directly as `--input <path>` — the
 *   CLI natively accepts a file path and reads it itself (no shell quoting needed).
 * - When only inline JSON is provided, it is written to a temp file first, then
 *   passed as a file path, to avoid PowerShell double-quote stripping.
 */
export function runHubbersInTerminal(
    args: string[],
    inputJson?: string,
    inputFilePath?: string,
): void {
    const { cmd, cmdArgs } = buildCommand(args);
    const repoPath = getRepoPath();
    const cwd = repoPath ?? vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;

    // Reuse or create the named Hubbers terminal
    let terminal = vscode.window.terminals.find(
        (t) => t.name === 'Hubbers' && t.exitStatus === undefined,
    );
    if (!terminal) {
        terminal = vscode.window.createTerminal({ name: 'Hubbers', cwd });
    }
    terminal.show(true);

    // Always cd to the correct working directory before each run so the CLI
    // finds application.yaml regardless of where the terminal was last used.
    if (cwd) {
        terminal.sendText(`Set-Location '${cwd.replace(/'/g, "''")}'`);
    }

    if (inputFilePath) {
        // The CLI natively accepts a file path for --input — pass it directly.
        const quotedPath = `'${inputFilePath.replace(/'/g, "''")}'`;
        terminal.sendText(`${cmd} ${cmdArgs.map(quoteArg).join(' ')} --input ${quotedPath}`);
    } else if (inputJson && inputJson.trim()) {
        // Inline JSON: write to a temp file so the CLI reads it as a file path,
        // avoiding PowerShell's double-quote stripping on external processes.
        let compact: string;
        try {
            compact = JSON.stringify(JSON.parse(inputJson));
        } catch {
            compact = inputJson.trim();
        }
        const tmpFile = path.join(os.tmpdir(), `hubbers-input-${Date.now()}.json`);
        fs.writeFileSync(tmpFile, compact, 'utf8');
        const quotedTmp = `'${tmpFile.replace(/'/g, "''")}'`;
        terminal.sendText(`${cmd} ${cmdArgs.map(quoteArg).join(' ')} --input ${quotedTmp}`);
    } else {
        terminal.sendText(`${cmd} ${cmdArgs.map(quoteArg).join(' ')}`);
    }
}

/**
 * Wraps an argument in double quotes if it contains spaces or special chars.
 * Uses cmd.exe-compatible escaping (internal quotes doubled).
 */
function quoteArg(arg: string): string {
    if (/[ \t"'{}\[\]|&<>^]/.test(arg)) {
        return `"${arg.replace(/"/g, '""')}"`;
    }
    return arg;
}

/**
 * Runs a Hubbers CLI command and streams its output to the Hubbers output channel.
 *
 * @param args  CLI arguments after the binary/jar path (e.g., ['agent', 'run', 'my.agent'])
 * @param inputJson  Optional JSON string passed via --input flag
 * @returns A Promise that resolves with the full stdout output, or rejects on non-zero exit.
 */
export function runHubbersCommand(args: string[], inputJson?: string): Promise<string> {
    const channel = getOutputChannel();
    channel.show(true);

    const allArgs = inputJson ? [...args, '--input', inputJson] : args;
    const { cmd, cmdArgs } = buildCommand(allArgs);
    const repoPath = getRepoPath();

    const spawnOptions: cp.SpawnOptions = {
        cwd: repoPath ?? vscode.workspace.workspaceFolders?.[0]?.uri.fsPath,
        env: { ...process.env },
        shell: false,
    };

    channel.appendLine(`\n▶ ${cmd} ${cmdArgs.join(' ')}`);
    channel.appendLine('─'.repeat(60));

    return new Promise((resolve, reject) => {
        const child = cp.spawn(cmd, cmdArgs, spawnOptions);
        const chunks: string[] = [];

        child.stdout?.on('data', (data: Buffer) => {
            const text = data.toString();
            chunks.push(text);
            channel.append(text);
        });

        child.stderr?.on('data', (data: Buffer) => {
            // Write stderr in a distinct prefix so it's easy to spot
            channel.append('[stderr] ' + data.toString());
        });

        child.on('error', (err: NodeJS.ErrnoException) => {
            if (err.code === 'ENOENT') {
                const notFound = new HubbersNotFoundError(cmd);
                channel.appendLine(notFound.message);
                reject(notFound);
            } else {
                const msg = `Failed to start Hubbers: ${err.message}`;
                channel.appendLine(msg);
                reject(new Error(msg));
            }
        });

        child.on('close', (code) => {
            channel.appendLine('─'.repeat(60));
            if (code === 0) {
                resolve(chunks.join(''));
            } else {
                const msg = `Hubbers exited with code ${code}`;
                channel.appendLine(msg);
                reject(new Error(msg));
            }
        });
    });
}
