package org.hubbers.security;

import lombok.extern.slf4j.Slf4j;
import org.hubbers.config.SecurityConfig;

import java.util.List;

/**
 * Service that enforces tool execution permissions based on allowlist/denylist configuration.
 *
 * <p>Permission logic:
 * <ol>
 *   <li>If the tool is in the {@code deniedTools} list, it is always blocked.</li>
 *   <li>If {@code allowedTools} is non-empty, the tool must appear in the list to execute.</li>
 *   <li>If both lists are empty/null, all tools are allowed (open by default).</li>
 * </ol>
 *
 * @since 0.1.0
 */
@Slf4j
public class ToolPermissionService {

    private final List<String> allowedTools;
    private final List<String> deniedTools;

    /**
     * Create a ToolPermissionService from security configuration.
     *
     * @param config the security configuration (may be null for open-by-default)
     */
    public ToolPermissionService(SecurityConfig config) {
        this.allowedTools = (config != null && config.getAllowedTools() != null)
                ? List.copyOf(config.getAllowedTools())
                : List.of();
        this.deniedTools = (config != null && config.getDeniedTools() != null)
                ? List.copyOf(config.getDeniedTools())
                : List.of();
        log.debug("ToolPermissionService initialized: allowed={}, denied={}", allowedTools, deniedTools);
    }

    /**
     * Check whether a given tool type is allowed to execute.
     *
     * @param toolType the tool type identifier (e.g., "shell.exec", "http.webhook")
     * @return true if the tool is permitted, false if blocked
     */
    public boolean isAllowed(String toolType) {
        if (toolType == null || toolType.isBlank()) {
            log.warn("Rejecting null/blank tool type");
            return false;
        }

        // Denylist always takes priority
        if (!deniedTools.isEmpty() && deniedTools.contains(toolType)) {
            log.info("Tool '{}' is blocked by deny list", toolType);
            return false;
        }

        // If allowlist is defined, tool must be on it
        if (!allowedTools.isEmpty() && !allowedTools.contains(toolType)) {
            log.info("Tool '{}' is not on the allow list", toolType);
            return false;
        }

        return true;
    }
}
