package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.manifest.tool.ToolManifest;

/**
 * Mock tool driver for "mock.weather". Returns fixed weather data for testing.
 * Rome → sunny/24°, all other cities → cloudy/18°.
 */
public class MockWeatherDriver implements ToolDriver {

    private final ObjectMapper mapper;

    public MockWeatherDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "mock.weather";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String city = input.path("city").asText("Unknown");
        ObjectNode result = mapper.createObjectNode();
        result.put("city", city);
        if ("Rome".equalsIgnoreCase(city) || "Roma".equalsIgnoreCase(city)) {
            result.put("temperature_celsius", 24.0);
            result.put("condition", "sunny");
        } else {
            result.put("temperature_celsius", 18.0);
            result.put("condition", "cloudy");
        }
        return result;
    }
}
