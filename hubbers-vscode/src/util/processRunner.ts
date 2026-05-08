import * as vscode from 'vscode';
import * as cp from 'child_process';
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
