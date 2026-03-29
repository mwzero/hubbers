package org.hubbers.manifest.agent;

public class ModelConfig {
    private String provider;
    private String name;
    private Double temperature;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
}
