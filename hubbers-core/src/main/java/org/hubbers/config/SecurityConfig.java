package org.hubbers.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Security configuration for the Hubbers runtime.
 *
 * <p>Controls tool access via allowlist/denylist, API key authentication,
 * and shell command restrictions.</p>
 *
 * @since 0.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityConfig {
    /** Explicit list of allowed tool types. If non-empty, only these tools may execute. */
    private List<String> allowedTools;

    /** List of denied tool types. Tools on this list are blocked regardless of allowedTools. */
    private List<String> deniedTools;

    /** API key required for authenticated web endpoints (Bearer token). */
    private String apiKey;

    /** Regex patterns of allowed shell commands for shell.exec tool driver. */
    private List<String> allowedCommands;
}
