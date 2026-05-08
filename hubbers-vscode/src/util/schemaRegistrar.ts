import * as vscode from 'vscode';
import * as path from 'path';

/** Schema file associations: artifact file name → schema file name inside /schemas/. */
const SCHEMA_ASSOCIATIONS: Array<{ filePattern: string; schemaFile: string }> = [
    { filePattern: 'agent.yaml', schemaFile: 'agent.schema.json' },
    { filePattern: 'tool.yaml', schemaFile: 'tool.schema.json' },
    { filePattern: 'pipeline.yaml', schemaFile: 'pipeline.schema.json' },
];

/**
 * Registers Hubbers schemas with the redhat.vscode-yaml extension (if installed).
 * The yamlValidation contribution in package.json handles the static case;
 * this function provides the programmatic path for custom URI handling.
 */
export function registerSchemas(context: vscode.ExtensionContext): void {
    const yamlExt = vscode.extensions.getExtension('redhat.vscode-yaml');
    if (!yamlExt) {
        // Fallback: package.json yamlValidation contribution already covers basic cases.
        return;
    }

    yamlExt.activate().then((api) => {
        if (!api?.registerContributor) {
            return;
        }

        api.registerContributor(
            'hubbers',
            (resource: string) => {
                const fileName = path.basename(resource);
                const assoc = SCHEMA_ASSOCIATIONS.find((a) => a.filePattern === fileName);
                if (!assoc) {
                    return undefined;
                }
                return vscode.Uri.joinPath(context.extensionUri, 'schemas', assoc.schemaFile).toString();
            },
            (_schemaUri: string) => {
                // Schema content is served from the URI returned above; no custom content needed.
                return undefined;
            },
        );
    });
}
