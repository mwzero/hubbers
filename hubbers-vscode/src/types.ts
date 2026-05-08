// Shared artifact types used across tree view, scanner, and commands.
export type ArtifactType = 'agent' | 'tool' | 'pipeline' | 'skill';

/** Represents a pre-stored JSON input file for an artifact. */
export interface InputFile {
    /** Display label (filename without extension). */
    label: string;
    /** Absolute path to the JSON file. */
    filePath: string;
}

export interface ArtifactItem {
    /** Artifact name as used by the Hubbers CLI (e.g., 'universal.task', 'rss.fetch'). */
    name: string;
    type: ArtifactType;
    /** Absolute path to the manifest file. */
    filePath: string;
}

/** Display labels for each artifact type category. */
export const TYPE_LABELS: Record<ArtifactType, string> = {
    agent: 'Agents',
    tool: 'Tools',
    pipeline: 'Pipelines',
    skill: 'Skills',
};

/** CLI sub-command word for each artifact type. */
export const TYPE_COMMANDS: Record<ArtifactType, string> = {
    agent: 'agent',
    tool: 'tool',
    pipeline: 'pipeline',
    skill: 'skill',
};
