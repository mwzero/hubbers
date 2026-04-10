package org.hubbers.cli;

import org.hubbers.forms.FormDefinition;
import org.hubbers.forms.FormField;

import java.util.HashMap;
import java.util.Map;

/**
 * High-level CLI form orchestration service.
 * Manages collection of form data through interactive terminal prompts.
 */
public class CliFormService {
    
    private final CliFormPrompt prompter;
    
    public CliFormService() {
        this.prompter = new CliFormPrompt();
    }
    
    /**
     * Collect form data from user through interactive prompts.
     * 
     * @param formDefinition the form to display
     * @return Map of field names to values
     */
    public Map<String, Object> collectFormData(FormDefinition formDefinition) {
        if (formDefinition == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> formData = new HashMap<>();
        
        // Display form header
        printFormHeader(formDefinition);
        
        // Prompt for each field
        if (formDefinition.getFields() != null) {
            for (FormField field : formDefinition.getFields()) {
                Object value = promptForField(field);
                if (value != null) {
                    formData.put(field.getName(), value);
                }
            }
        }
        
        System.out.println("\n✅ Form completed\n");
        return formData;
    }
    
    /**
     * Display form title and description.
     */
    private void printFormHeader(FormDefinition formDefinition) {
        System.out.println("\n" + "=".repeat(60));
        
        if (formDefinition.getTitle() != null && !formDefinition.getTitle().isEmpty()) {
            System.out.println("📋 " + formDefinition.getTitle());
        }
        
        if (formDefinition.getDescription() != null && !formDefinition.getDescription().isEmpty()) {
            System.out.println("   " + formDefinition.getDescription());
        }
        
        System.out.println("=".repeat(60) + "\n");
    }
    
    /**
     * Prompt for a single field based on its type.
     */
    private Object promptForField(FormField field) {
        String type = field.getType() != null ? field.getType().toLowerCase() : "text";
        String label = field.getLabel() != null ? field.getLabel() : field.getName();
        boolean required = field.isRequired();
        
        try {
            return switch (type) {
                case "text" -> prompter.promptText(
                    label,
                    field.getPlaceholder(),
                    getDefaultAsString(field),
                    required
                );
                
                case "textarea" -> prompter.promptTextarea(
                    label,
                    field.getPlaceholder(),
                    getDefaultAsString(field),
                    required
                );
                
                case "number" -> prompter.promptNumber(
                    label,
                    getDefaultAsNumber(field),
                    field.getMin(),
                    field.getMax(),
                    required
                );
                
                case "select" -> prompter.promptSelect(
                    label,
                    field.getOptions(),
                    getDefaultAsString(field)
                );
                
                case "checkbox" -> prompter.promptCheckbox(
                    label,
                    getDefaultAsBoolean(field)
                );
                
                case "slider" -> prompter.promptSlider(
                    label,
                    field.getMin(),
                    field.getMax(),
                    field.getStep(),
                    getDefaultAsNumber(field)
                );
                
                default -> {
                    System.err.println("⚠️  Warning: Unknown field type '" + type + "', treating as text");
                    yield prompter.promptText(label, field.getPlaceholder(), getDefaultAsString(field), required);
                }
            };
        } catch (Exception e) {
            System.err.println("❌ Error collecting field '" + field.getName() + "': " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get default value as string.
     */
    private String getDefaultAsString(FormField field) {
        if (field.getDefaultValue() == null) {
            return null;
        }
        return field.getDefaultValue().toString();
    }
    
    /**
     * Get default value as number.
     */
    private Number getDefaultAsNumber(FormField field) {
        Object defaultValue = field.getDefaultValue();
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue instanceof Number) {
            return (Number) defaultValue;
        }
        try {
            return Double.parseDouble(defaultValue.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Get default value as boolean.
     */
    private boolean getDefaultAsBoolean(FormField field) {
        Object defaultValue = field.getDefaultValue();
        if (defaultValue == null) {
            return false;
        }
        if (defaultValue instanceof Boolean) {
            return (Boolean) defaultValue;
        }
        String str = defaultValue.toString().toLowerCase();
        return str.equals("true") || str.equals("yes") || str.equals("y") || str.equals("1");
    }
}
