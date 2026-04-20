package org.hubbers.forms;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the state of an interactive form session.
 * Used for human-in-the-loop workflows.
 */
@Data
@NoArgsConstructor
public class FormSession {
    
    private String sessionId;
    private String executionId;
    private String artifactType;
    private String artifactName;
    private FormDefinition formDefinition;
    private FormStage stage; // BEFORE, DURING, AFTER
    private JsonNode originalInput;
    private Map<String, Object> formData = new HashMap<>();
    private long createdAt = Instant.now().toEpochMilli();
    private Long submittedAt;
    private FormSessionStatus status = FormSessionStatus.AWAITING_INPUT;
    
    // For pipeline during forms
    private Integer pausedAtStep;
    private String pausedStepId;
    
    public FormSession(String sessionId, String executionId, String artifactType, String artifactName) {
        this();
        this.sessionId = sessionId;
        this.executionId = executionId;
        this.artifactType = artifactType;
        this.artifactName = artifactName;
    }
    
    public void markSubmitted() {
        this.submittedAt = Instant.now().toEpochMilli();
        this.status = FormSessionStatus.SUBMITTED;
    }
}
