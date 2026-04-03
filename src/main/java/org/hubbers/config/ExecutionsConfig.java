package org.hubbers.config;

public class ExecutionsConfig {
    private String path = "./executions"; // default to ./executions relative to repo
    private Integer retentionDays;
    private Integer maxConcurrent;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Integer getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(Integer maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }
}
