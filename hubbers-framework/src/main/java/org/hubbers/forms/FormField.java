package org.hubbers.forms;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Defines the structure of a form field.
 */
@Data
public class FormField {
    
    private String name;
    private String type; // "text", "number", "textarea", "select", "checkbox", "slider"
    private String label;
    private String placeholder;
    private Object defaultValue;
    private boolean required;
    private Map<String, Object> validation; // min, max, pattern, etc.
    private List<FormOption> options; // For select fields
    
    // For slider
    private Number min;
    private Number max;
    private Number step;
}
