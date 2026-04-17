package org.hubbers.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Factory for creating and caching Jackson ObjectMapper instances.
 * 
 * <p>This factory maintains singleton instances of ObjectMapper for both
 * JSON and YAML processing to avoid the overhead of repeatedly creating
 * and configuring mappers throughout the application.</p>
 * 
 * <p>The mappers are configured with:
 * <ul>
 *   <li>All Java modules auto-registered (e.g., JavaTimeModule)</li>
 *   <li>Unknown properties ignored (lenient parsing)</li>
 * </ul>
 * 
 * @since 0.1.0
 */
public final class JacksonFactory {
    
    private static final ObjectMapper JSON_MAPPER = createJsonMapper();
    private static final ObjectMapper YAML_MAPPER = createYamlMapper();
    
    private JacksonFactory() {
    }

    /**
     * Get the singleton JSON ObjectMapper instance.
     * 
     * @return the shared JSON mapper
     */
    public static ObjectMapper jsonMapper() {
        return JSON_MAPPER;
    }

    /**
     * Get the singleton YAML ObjectMapper instance.
     * 
     * @return the shared YAML mapper
     */
    public static ObjectMapper yamlMapper() {
        return YAML_MAPPER;
    }
    
    private static ObjectMapper createJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
    
    private static ObjectMapper createYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
