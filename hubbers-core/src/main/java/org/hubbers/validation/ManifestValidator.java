package org.hubbers.validation;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.manifest.skill.SkillManifest;
import org.hubbers.manifest.tool.ToolManifest;

public class ManifestValidator {

    public ValidationResult validateAgent(AgentManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getAgent() == null) {
            result.addError("Agent metadata missing");
            return result;
        }
        if (manifest.getAgent().getName() == null || manifest.getAgent().getName().isBlank()) {
            result.addError("Agent name missing");
        }
        if (manifest.getModel() == null || manifest.getModel().getProvider() == null
                || manifest.getModel().getProvider().isBlank()) {
            result.addError("Agent model provider missing");
        }
        if (manifest.getModel() == null || manifest.getModel().getName() == null
                || manifest.getModel().getName().isBlank()) {
            result.addError("Agent model name missing");
        }
        if (manifest.getInstructions() == null || manifest.getInstructions().getSystemPrompt() == null) {
            result.addError("Agent instructions missing");
        }
        if (manifest.getInput() == null || manifest.getInput().getSchema() == null) {
            result.addError("Agent input schema missing");
        }
        if (manifest.getOutput() == null || manifest.getOutput().getSchema() == null) {
            result.addError("Agent output schema missing");
        }
        return result;
    }

    public ValidationResult validateTool(ToolManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getTool() == null) {
            result.addError("Tool metadata missing");
            return result;
        }
        if (manifest.getTool().getName() == null || manifest.getTool().getName().isBlank()) {
            result.addError("Tool name missing");
        }
        if (manifest.getType() == null || manifest.getType().isBlank()) {
            result.addError("Tool type missing");
        }
        if (manifest.getInput() == null || manifest.getInput().getSchema() == null) {
            result.addError("Tool input schema missing");
        }
        if (manifest.getOutput() == null || manifest.getOutput().getSchema() == null) {
            result.addError("Tool output schema missing");
        }
        return result;
    }

    public ValidationResult validatePipeline(PipelineManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getPipeline() == null) {
            result.addError("Pipeline metadata missing");
            return result;
        }
        if (manifest.getPipeline().getName() == null || manifest.getPipeline().getName().isBlank()) {
            result.addError("Pipeline name missing");
        }
        if (manifest.getSteps() == null || manifest.getSteps().isEmpty()) {
            result.addError("Pipeline steps missing");
            return result;
        }
        for (PipelineStep step : manifest.getSteps()) {
            boolean hasAgent = step.getAgent() != null && !step.getAgent().isBlank();
            boolean hasTool = step.getTool() != null && !step.getTool().isBlank();
            if (hasAgent == hasTool) {
                result.addError("Step " + step.getId() + " must contain agent XOR tool");
            }
        }
        return result;
    }

    public ValidationResult validateSkill(SkillManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getFrontmatter() == null) {
            result.addError("Skill metadata missing");
            return result;
        }
        if (manifest.getFrontmatter().getName() == null || manifest.getFrontmatter().getName().isBlank()) {
            result.addError("Skill name missing");
        }
        if (manifest.getBody() == null || manifest.getBody().isBlank()) {
            result.addError("Skill instructions missing");
        }
        return result;
    }
}
