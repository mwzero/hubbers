package org.hubbers.cli;

import org.hubbers.forms.FormOption;

import java.io.Console;
import java.util.List;
import java.util.Scanner;

/**
 * Low-level CLI form prompting service for interactive terminal input.
 * Supports all form field types with validation and default values.
 */
public class CliFormPrompt {
    
    private final Console console;
    private final Scanner scanner;
    
    public CliFormPrompt() {
        this.console = System.console();
        // Fallback to Scanner if no console (e.g., running from IDE)
        this.scanner = (console == null) ? new Scanner(System.in) : null;
    }
    
    /**
     * Prompt for text input.
     */
    public String promptText(String label, String placeholder, String defaultValue, boolean required) {
        while (true) {
            String prompt = buildPrompt(label, defaultValue, placeholder);
            String input = readLine(prompt);
            
            if (input == null || input.trim().isEmpty()) {
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    return defaultValue;
                }
                if (required) {
                    System.err.println("❌ Error: Field '" + label + "' is required");
                    continue;
                }
                return "";
            }
            return input.trim();
        }
    }
    
    /**
     * Prompt for multi-line textarea input.
     * In CLI, we accept single-line input or file path prefixed with @
     */
    public String promptTextarea(String label, String placeholder, String defaultValue, boolean required) {
        System.out.println("💡 Tip: Enter text directly, or use @/path/to/file.txt to load from file");
        
        while (true) {
            String prompt = buildPrompt(label, defaultValue, placeholder);
            String input = readLine(prompt);
            
            if (input == null || input.trim().isEmpty()) {
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    return defaultValue;
                }
                if (required) {
                    System.err.println("❌ Error: Field '" + label + "' is required");
                    continue;
                }
                return "";
            }
            
            input = input.trim();
            
            // Handle file path input
            if (input.startsWith("@")) {
                String filePath = input.substring(1);
                try {
                    String content = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
                    return content;
                } catch (Exception e) {
                    System.err.println("❌ Error: Cannot read file: " + e.getMessage());
                    continue;
                }
            }
            
            return input;
        }
    }
    
    /**
     * Prompt for number input with validation.
     */
    public Number promptNumber(String label, Number defaultValue, Number min, Number max, boolean required) {
        String rangeInfo = "";
        if (min != null && max != null) {
            rangeInfo = " [" + min + "-" + max + "]";
        } else if (min != null) {
            rangeInfo = " [min: " + min + "]";
        } else if (max != null) {
            rangeInfo = " [max: " + max + "]";
        }
        
        while (true) {
            String prompt = buildPrompt(label + rangeInfo, defaultValue != null ? defaultValue.toString() : null, null);
            String input = readLine(prompt);
            
            if (input == null || input.trim().isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                if (required) {
                    System.err.println("❌ Error: Field '" + label + "' is required");
                    continue;
                }
                return null;
            }
            
            try {
                // Try parsing as double first (handles both int and decimal)
                double value = Double.parseDouble(input.trim());
                
                // Validate range
                if (min != null && value < min.doubleValue()) {
                    System.err.println("❌ Error: Value must be at least " + min);
                    continue;
                }
                if (max != null && value > max.doubleValue()) {
                    System.err.println("❌ Error: Value must be at most " + max);
                    continue;
                }
                
                // Return as integer if it's a whole number
                if (value == Math.floor(value)) {
                    return (int) value;
                }
                return value;
                
            } catch (NumberFormatException e) {
                System.err.println("❌ Error: Invalid number format");
            }
        }
    }
    
    /**
     * Prompt for select/dropdown input.
     * Displays numbered list of options, accepts index or value.
     */
    public String promptSelect(String label, List<FormOption> options, String defaultValue) {
        if (options == null || options.isEmpty()) {
            System.err.println("❌ Error: No options defined for select field");
            return defaultValue;
        }
        
        // Display options
        System.out.println(label + ":");
        for (int i = 0; i < options.size(); i++) {
            FormOption option = options.get(i);
            String optionValue = option.getValue() != null ? option.getValue().toString() : "";
            String marker = (optionValue.equals(defaultValue)) ? " (default)" : "";
            System.out.println("  " + (i + 1) + ") " + option.getLabel() + marker);
        }
        
        while (true) {
            String prompt = "Select option (1-" + options.size() + ")";
            if (defaultValue != null) {
                prompt += " [" + defaultValue + "]";
            }
            prompt += ": ";
            
            String input = readLine(prompt);
            
            if (input == null || input.trim().isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                System.err.println("❌ Error: Selection is required");
                continue;
            }
            
            input = input.trim();
            
            // Try parsing as index (1-based)
            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < options.size()) {
                    Object value = options.get(index).getValue();
                    return value != null ? value.toString() : "";
                }
                System.err.println("❌ Error: Invalid option number. Choose 1-" + options.size());
                continue;
            } catch (NumberFormatException e) {
                // Not a number, try matching by value or label
            }
            
            // Try matching by value or label
            for (FormOption option : options) {
                String optionValue = option.getValue() != null ? option.getValue().toString() : "";
                String optionLabel = option.getLabel() != null ? option.getLabel() : "";
                
                if (optionValue.equalsIgnoreCase(input) || optionLabel.equalsIgnoreCase(input)) {
                    return optionValue;
                }
            }
            
            System.err.println("❌ Error: Invalid selection. Enter option number (1-" + options.size() + ") or value");
        }
    }
    
    /**
     * Prompt for checkbox/boolean input.
     * Accepts: y/n, yes/no, true/false (case insensitive)
     */
    public boolean promptCheckbox(String label, boolean defaultValue) {
        String defaultStr = defaultValue ? "y" : "n";
        
        while (true) {
            String prompt = buildPrompt(label + " (y/n)", defaultStr, null);
            String input = readLine(prompt);
            
            if (input == null || input.trim().isEmpty()) {
                return defaultValue;
            }
            
            input = input.trim().toLowerCase();
            
            if (input.equals("y") || input.equals("yes") || input.equals("true") || input.equals("1")) {
                return true;
            }
            if (input.equals("n") || input.equals("no") || input.equals("false") || input.equals("0")) {
                return false;
            }
            
            System.err.println("❌ Error: Enter 'y' (yes) or 'n' (no)");
        }
    }
    
    /**
     * Prompt for slider input (numeric value within range).
     * Functionally equivalent to number field with enforced min/max.
     */
    public Number promptSlider(String label, Number min, Number max, Number step, Number defaultValue) {
        String rangeInfo = " [" + min + "-" + max;
        if (step != null && step.doubleValue() != 1.0) {
            rangeInfo += ", step " + step;
        }
        rangeInfo += "]";
        
        while (true) {
            String prompt = buildPrompt(label + rangeInfo, defaultValue != null ? defaultValue.toString() : null, null);
            String input = readLine(prompt);
            
            if (input == null || input.trim().isEmpty()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                System.err.println("❌ Error: Value is required");
                continue;
            }
            
            try {
                double value = Double.parseDouble(input.trim());
                
                // Validate range
                if (value < min.doubleValue()) {
                    System.err.println("❌ Error: Value must be at least " + min);
                    continue;
                }
                if (value > max.doubleValue()) {
                    System.err.println("❌ Error: Value must be at most " + max);
                    continue;
                }
                
                // Validate step
                if (step != null && step.doubleValue() != 0) {
                    double stepValue = step.doubleValue();
                    double minValue = min.doubleValue();
                    double offset = (value - minValue) % stepValue;
                    if (Math.abs(offset) > 0.0001 && Math.abs(offset - stepValue) > 0.0001) {
                        System.err.println("❌ Error: Value must be in increments of " + step);
                        continue;
                    }
                }
                
                // Return as integer if it's a whole number
                if (value == Math.floor(value)) {
                    return (int) value;
                }
                return value;
                
            } catch (NumberFormatException e) {
                System.err.println("❌ Error: Invalid number format");
            }
        }
    }
    
    /**
     * Build prompt string with label, default, and placeholder.
     */
    private String buildPrompt(String label, String defaultValue, String placeholder) {
        StringBuilder sb = new StringBuilder(label);
        
        if (placeholder != null && !placeholder.isEmpty() && (defaultValue == null || defaultValue.isEmpty())) {
            sb.append(" (").append(placeholder).append(")");
        }
        
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" [").append(defaultValue).append("]");
        }
        
        sb.append(": ");
        return sb.toString();
    }
    
    /**
     * Read line from console or scanner.
     */
    private String readLine(String prompt) {
        if (console != null) {
            return console.readLine(prompt);
        } else {
            System.out.print(prompt);
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        }
    }
}
