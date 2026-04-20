package org.hubbers.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the artifact repository path consistently across CLI, web, and tests.
 */
public final class RepoPathResolver {
    public static final String DEFAULT_REPO_PATH = "hubbers-repo/src/main/resources/repo";

    private RepoPathResolver() {
    }

    public static String resolve(String repoPath) {
        if (repoPath != null && !repoPath.isBlank()) {
            return resolveExistingPath(repoPath);
        }

        String envRepo = System.getenv("HUBBERS_REPO");
        if (envRepo != null && !envRepo.isBlank()) {
            return resolveExistingPath(envRepo);
        }

        return resolveExistingPath(DEFAULT_REPO_PATH);
    }

    private static String resolveExistingPath(String repoPath) {
        Path configured = Paths.get(repoPath);
        if (configured.isAbsolute()) {
            return configured.normalize().toString();
        }

        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(configured).normalize();
            if (Files.exists(candidate.resolve("application.yaml"))) {
                return candidate.toString();
            }
            current = current.getParent();
        }

        return configured.normalize().toString();
    }
}
