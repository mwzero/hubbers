export type ArtifactType = 'agent' | 'tool' | 'pipeline' | 'skill';

export interface Artifact {
  label: string;
  type: ArtifactType;
  path: string;
  name: string;
}

export interface RepoModel {
  agents: Artifact[];
  tools: Artifact[];
  pipelines: Artifact[];
  skills: Artifact[];
}

export interface Execution {
  executionId: string;
  status: 'SUCCESS' | 'FAILED' | 'RUNNING' | 'PAUSED';
  artifactType: ArtifactType;
  artifactName: string;
  startedAt: number;
  endedAt?: number;
}

export interface Step {
  name: string;
  hasInput: boolean;
  hasOutput: boolean;
}

export interface FormFieldOption {
  label: string;
  value: string | number | boolean;
}

export interface FormField {
  name: string;
  label?: string;
  type: 'text' | 'textarea' | 'number' | 'slider' | 'checkbox' | 'select';
  required?: boolean;
  placeholder?: string;
  defaultValue?: any;
  min?: number;
  max?: number;
  step?: number;
  options?: FormFieldOption[];
}

export interface FormDef {
  title?: string;
  description?: string;
  fields: FormField[];
}

export interface StepInputMapping {
  key: string;
  expression: string;
}

export interface PipelineStep {
  id: string;
  targetType: ArtifactType;
  target: string;
  inputMapping: StepInputMapping[];
}

export interface ValidationResult {
  valid: boolean;
  errors?: string[];
}

export interface ToolDriverInfo {
  type: string;
  label: string;
  description: string;
  risk: 'network' | 'filesystem' | 'high-risk' | 'data' | 'interactive' | 'storage' | string;
}

export interface ModelProviderInfo {
  id: string;
  label: string;
  local: boolean;
  configured: boolean;
  defaultModel?: string;
}

export interface ArtifactStatus {
  status: 'draft' | 'valid' | 'invalid' | 'certified' | 'deprecated' | string;
  certified: boolean;
  valid: boolean;
  errors: string[];
}
