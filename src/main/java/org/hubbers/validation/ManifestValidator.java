package org.hubbers.validation;

import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.hubbers.manifest.tool.ToolManifest;

public class ManifestValidator {

    public ValidationResult validateAgent(AgentManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getAgent() == null) {
            result.addError("Agent metadata missing");
            return result;
        }
        if (manifest.getModel() == null || manifest.getModel().getProvider() == null) {
            result.addError("Agent model provider missing");
        }
        if (manifest.getInstructions() == null || manifest.getInstructions().getSystemPrompt() == null) {
            result.addError("Agent instructions missing");
        }
        if (manifest.getExamples() == null || manifest.getExamples().isEmpty()) {
            result.addError("Agent examples missing");
        }
        return result;
    }

    public ValidationResult validateTool(ToolManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getTool() == null) {
            result.addError("Tool metadata missing");
            return result;
        }
        if (!"http".equals(manifest.getType())
                && !"docker".equals(manifest.getType())
                && !"rss".equals(manifest.getType())
                && !"vector.lucene.enrich".equals(manifest.getType())
                && !"vector.lucene.upsert".equals(manifest.getType())
                && !"vector.lucene.search".equals(manifest.getType())
                && !"lucene.kv".equals(manifest.getType())
                && !"browser.pinchtab".equals(manifest.getType())
                && !"csv.write".equals(manifest.getType())
                && !"csv.read".equals(manifest.getType())) {
            result.addError("Invalid tool type: " + manifest.getType());
        }
        if (manifest.getConfig() == null || manifest.getConfig().isEmpty()) {
            result.addError("Tool config missing");
        }
        if (manifest.getExamples() == null || manifest.getExamples().isEmpty()) {
            result.addError("Tool examples missing");
        }
        return result;
    }

    public ValidationResult validatePipeline(PipelineManifest manifest) {
        ValidationResult result = ValidationResult.ok();
        if (manifest == null || manifest.getPipeline() == null) {
            result.addError("Pipeline metadata missing");
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
}
