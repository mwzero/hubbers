package org.hubbers.tool;

import java.util.List;

/**
 * Service-provider interface for contributing tool drivers to the runtime.
 */
public interface ToolDriverProvider {
    /**
     * Create the tool drivers exposed by this provider.
     *
     * @param context shared runtime dependencies
     * @return drivers to register with the runtime
     */
    List<ToolDriver> createDrivers(ToolDriverContext context);
}
