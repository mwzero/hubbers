package org.hubbers.forms;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines an option for select/radio fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormOption {
    
    private String label;
    private Object value;
}
