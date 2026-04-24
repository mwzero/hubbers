package org.hubbers.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the artifact repository path consistently across CLI, web, and tests.
 *
 * <p>Resolution precedence:
 * <ol>
 *   <li>Explicit argument (CLI flag or programmatic)</li>
 *   <li>{@code HUBBERS_REPO} environment variable</li>
 *   <li>{@code hubbers.repo} system property</li>
 *   <li>Default path: {@value #DEFAULT_REPO_PATH}</li>
 * </ol>
 */
@Slf4j
public final class RepoPathResolver {
    public static final String DEFAULT_REPO_PATH = "hubbers-repo/src/main/resources/repo";
    public static final String ENV_VAR = "HUBBERS_REPO";
    public static final String SYSTEM_PROPERTY = "hubbers.repo";

    private RepoPathResolver() {
    }

    /**
     * Resolve the repository path using the precedence chain.
     *
     * @param repoPath explicit path (highest priority), may be {@code null}
     * @return resolved absolute or relative path to the repo directory
     */
    public static String resolve(String repoPath) {
        if (repoPath != null && !repoPath.isBlank()) {
            log.debug("Repo path resolved from explicit argument: {}", repoPath);
            return resolveExistingPath(repoPath);
        }

        String envRepo = System.getenv(ENV_VAR);
        if (envRepo != null && !envRepo.isBlank()) {
            log.debug("Repo path resolved from environment variable {}: {}", ENV_VAR, envRepo);
            return resolveExistingPath(envRepo);
        }

        String sysProp = System.getProperty(SYSTEM_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            log.debug("Repo path resolved from system property {}: {}", SYSTEM_PROPERTY, sysProp);
            return resolveExistingPath(sysProp);
        }

        log.debug("Repo path resolved from default: {}", DEFAULT_REPO_PATH);
        return resolveExistingPath(DEFAULT_REPO_PATH);
    }

    private static String resolveExistingPath(String repoPath) {
        Path configured = Paths.get(repoPath);
        if (configured.isAbsolute()) {
            String resolved = configured.normalize().toString();
            validateRepoStructure(Path.of(resolved));
            return resolved;
        }

        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(configured).normalize();
            if (Files.exists(candidate.resolve("application.yaml"))) {
                validateRepoStructure(candidate);
                return candidate.toString();
            }
            current = current.getParent();
        }

        return configured.normalize().toString();
    }

    /**
     * Validates that the resolved path contains the expected repo directory structure.
     * Logs warnings if expected directories are missing — does not throw.
     *
     * @param repoPath the resolved repo root path
     */
    private static void validateRepoStructure(Path repoPath) {
        if (!Files.isDirectory(repoPath)) {
            log.warn("Repo path does not exist or is not a directory: {}", repoPath);
            return;
        }
        boolean hasAgents = Files.isDirectory(repoPath.resolve("agents"));
        boolean hasTools = Files.isDirectory(repoPath.resolve("tools"));
        if (!hasAgents && !hasTools) {
            log.warn("Repo path '{}' is missing expected 'agents/' and 'tools/' directories", repoPath);
        }
    }
}
