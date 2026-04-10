package org.hubbers.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private final List<String> errors = new ArrayList<>();

    public static ValidationResult ok() {
        return new ValidationResult();
    }

    public void addError(String error) { errors.add(error); }
    public boolean isValid() { return errors.isEmpty(); }
    public List<String> getErrors() { return errors; }
}
