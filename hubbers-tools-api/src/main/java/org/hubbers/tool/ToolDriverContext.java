package org.hubbers.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hubbers.config.AppConfig;

import java.net.http.HttpClient;

/**
 * Shared runtime dependencies for constructing tool drivers.
 *
 * @param jsonMapper application JSON mapper
 * @param httpClient shared HTTP client
 * @param appConfig resolved application configuration
 */
public record ToolDriverContext(
        ObjectMapper jsonMapper,
        HttpClient httpClient,
        AppConfig appConfig
) {
}
