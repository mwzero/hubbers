package org.hubbers.manifest.common;

public class PropertyDefinition {
    private String type;
    private boolean required;
    private String description;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
