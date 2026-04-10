package org.hubbers.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.util.JacksonFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing form-based human-in-the-loop interactions.
 * Acts as a bridge between Hubbers and JUI form components.
 */
public class JuiFormService {
    
    private final FormSessionStore sessionStore;
    private final ObjectMapper jsonMapper = JacksonFactory.jsonMapper();
    
    public JuiFormService(FormSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }
    
    /**
     * Creates a new form session.
     * 
     * @param executionId the execution ID
     * @param artifactType the artifact type (agent, tool, pipeline)
     * @param artifactName the artifact name
     * @param formDefinition the form to display
     * @param stage when the form should be shown (BEFORE, DURING, AFTER)
     * @param originalInput the original input data
     * @return the form session
     */
    public FormSession createFormSession(String executionId, String artifactType, String artifactName,
                                         FormDefinition formDefinition, FormStage stage, JsonNode originalInput) {
        String sessionId = UUID.randomUUID().toString();
        
        FormSession session = new FormSession(sessionId, executionId, artifactType, artifactName);
        session.setFormDefinition(formDefinition);
        session.setStage(stage);
        session.setOriginalInput(originalInput);
        
        sessionStore.put(sessionId, session);
        return session;
    }
    
    /**
     * Submits form data for a session.
     * 
     * @param sessionId the session ID
     * @param formData the submitted form data
     * @return the updated session
     */
    public FormSession submitForm(String sessionId, Map<String, Object> formData) {
        FormSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Form session not found: " + sessionId);
        }
        
        if (session.getStatus() == FormSessionStatus.SUBMITTED) {
            throw new IllegalStateException("Form already submitted for session: " + sessionId);
        }
        
        // Validate form data against form definition
        validateFormData(session.getFormDefinition(), formData);
        
        session.setFormData(formData);
        session.markSubmitted();
        
        return session;
    }
    
    /**
     * Gets a form session by ID.
     */
    public FormSession getSession(String sessionId) {
        return sessionStore.get(sessionId);
    }
    
    /**
     * Cancels a form session.
     */
    public void cancelSession(String sessionId) {
        FormSession session = sessionStore.get(sessionId);
        if (session != null) {
            session.setStatus(FormSessionStatus.CANCELLED);
        }
    }
    
    /**
     * Merges form data with original input.
     * Used for BEFORE forms to combine user input with execution input.
     * 
     * @param originalInput the original input JSON
     * @param formData the form data
     * @return merged JSON input
     */
    public JsonNode mergeFormDataWithInput(JsonNode originalInput, Map<String, Object> formData) {
        ObjectNode merged = originalInput != null && originalInput.isObject() 
            ? (ObjectNode) originalInput.deepCopy()
            : jsonMapper.createObjectNode();
        
        // Merge form data into input
        formData.forEach((key, value) -> {
            merged.set(key, jsonMapper.valueToTree(value));
        });
        
        return merged;
    }
    
    /**
     * Converts form definition to a JSON structure for frontend rendering.
     * This allows the frontend to render forms dynamically.
     * 
     * @param formDefinition the form definition
     * @return JSON representation for frontend
     */
    public Map<String, Object> toFrontendFormat(FormDefinition formDefinition) {
        Map<String, Object> result = new HashMap<>();
        result.put("title", formDefinition.getTitle());
        result.put("description", formDefinition.getDescription());
        result.put("fields", formDefinition.getFields());
        return result;
    }
    
    /**
     * Validates form data against form definition.
     * 
     * @param formDefinition the form definition
     * @param formData the submitted data
     * @throws IllegalArgumentException if validation fails
     */
    private void validateFormData(FormDefinition formDefinition, Map<String, Object> formData) {
        if (formDefinition == null || formDefinition.getFields() == null) {
            return;
        }
        
        for (FormField field : formDefinition.getFields()) {
            String fieldName = field.getName();
            
            // Check required fields
            if (field.isRequired() && !formData.containsKey(fieldName)) {
                throw new IllegalArgumentException("Required field missing: " + fieldName);
            }
            
            // Additional validation can be added here (type checking, min/max, pattern, etc.)
        }
    }
    
    /**
     * Gets the session store.
     */
    public FormSessionStore getSessionStore() {
        return sessionStore;
    }
}
