package org.hubbers.forms;

import lombok.Data;

import java.util.List;

/**
 * Defines when and how forms should be displayed during execution.
 */
@Data
public class FormDefinition {
    
    private String title;
    private String description;
    private List<FormField> fields;
}
