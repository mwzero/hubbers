package org.hubbers.web;

public enum ManifestType {
    AGENT("agents", "agent.yaml"),
    TOOL("tools", "tool.yaml"),
    PIPELINE("pipelines", "pipeline.yaml");

    private final String folder;
    private final String filename;

    ManifestType(String folder, String filename) {
        this.folder = folder;
        this.filename = filename;
    }

    public String folder() {
        return folder;
    }

    public String filename() {
        return filename;
    }

    public static ManifestType fromPath(String value) {
        return switch (value.toLowerCase()) {
            case "agent", "agents" -> AGENT;
            case "tool", "tools" -> TOOL;
            case "pipeline", "pipelines" -> PIPELINE;
            default -> throw new IllegalArgumentException("Unsupported manifest type: " + value);
        };
    }
}
