package org.hubbers.tool;

import org.hubbers.util.JacksonFactory;

import java.util.List;

/**
 * ServiceLoader-registered provider that supplies {@link MockWeatherDriver}
 * on the test classpath. Discovered automatically by ServiceLoader during tests.
 */
public class MockWeatherDriverProvider implements ToolDriverProvider {

    @Override
    public List<ToolDriver> createDrivers(ToolDriverContext context) {
        var mapper = context.jsonMapper() != null ? context.jsonMapper() : JacksonFactory.jsonMapper();
        return List.of(new MockWeatherDriver(mapper));
    }
}
