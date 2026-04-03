package org.hubbers.forms;

import lombok.Data;

/**
 * Specifies when forms should be shown during artifact execution.
 */
@Data
public class FormTrigger {
    
    private FormDefinition before;  // Show form before execution
    private FormDefinition during;  // Show form during execution (human-in-the-loop)
    private FormDefinition after;   // Show form after execution (for output review/confirmation)
    
    public boolean hasAnyForm() {
        return before != null || during != null || after != null;
    }
}
