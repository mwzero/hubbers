package org.hubbers.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepoPathResolver Tests")
class RepoPathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should return explicit path when provided")
    void testResolve_WithExplicitPath_ReturnsPath() throws IOException {
        // Given — create a valid repo structure
        Path repoDir = tempDir.resolve("my-repo");
        Files.createDirectories(repoDir.resolve("agents"));
        Files.createDirectories(repoDir.resolve("tools"));
        Files.createFile(repoDir.resolve("application.yaml"));

        // When
        String resolved = RepoPathResolver.resolve(repoDir.toString());

        // Then
        assertEquals(repoDir.normalize().toString(), resolved,
                "Should return the explicit path as-is (normalized)");
    }

    @Test
    @DisplayName("Should use system property when no explicit path given")
    void testResolve_WithSystemProperty_UsesProperty() throws IOException {
        // Given
        Path repoDir = tempDir.resolve("sys-prop-repo");
        Files.createDirectories(repoDir.resolve("agents"));
        Files.createDirectories(repoDir.resolve("tools"));
        Files.createFile(repoDir.resolve("application.yaml"));

        String previousValue = System.getProperty(RepoPathResolver.SYSTEM_PROPERTY);
        try {
            System.setProperty(RepoPathResolver.SYSTEM_PROPERTY, repoDir.toString());

            // When
            String resolved = RepoPathResolver.resolve(null);

            // Then
            assertEquals(repoDir.normalize().toString(), resolved,
                    "Should resolve from system property");
        } finally {
            // Restore previous state
            if (previousValue != null) {
                System.setProperty(RepoPathResolver.SYSTEM_PROPERTY, previousValue);
            } else {
                System.clearProperty(RepoPathResolver.SYSTEM_PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("Should prefer explicit path over system property")
    void testResolve_ExplicitOverProperty_PrefersExplicit() throws IOException {
        // Given
        Path explicitDir = tempDir.resolve("explicit-repo");
        Path propertyDir = tempDir.resolve("property-repo");
        Files.createDirectories(explicitDir.resolve("agents"));
        Files.createFile(explicitDir.resolve("application.yaml"));
        Files.createDirectories(propertyDir.resolve("agents"));
        Files.createFile(propertyDir.resolve("application.yaml"));

        String previousValue = System.getProperty(RepoPathResolver.SYSTEM_PROPERTY);
        try {
            System.setProperty(RepoPathResolver.SYSTEM_PROPERTY, propertyDir.toString());

            // When
            String resolved = RepoPathResolver.resolve(explicitDir.toString());

            // Then
            assertEquals(explicitDir.normalize().toString(), resolved,
                    "Explicit path should take precedence over system property");
        } finally {
            if (previousValue != null) {
                System.setProperty(RepoPathResolver.SYSTEM_PROPERTY, previousValue);
            } else {
                System.clearProperty(RepoPathResolver.SYSTEM_PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("Should ignore blank explicit path and fall through")
    void testResolve_WithBlankPath_FallsThrough() {
        // When — blank string should be treated as null
        String resolved = RepoPathResolver.resolve("   ");

        // Then — should not throw, falls through to env/sysprop/default
        assertNotNull(resolved, "Should always return a non-null path");
    }

    @Test
    @DisplayName("Should normalize absolute paths")
    void testResolve_AbsolutePath_Normalized() throws IOException {
        // Given — create path with redundant segments
        Path repoDir = tempDir.resolve("norm-repo");
        Files.createDirectories(repoDir);
        String pathWithDots = repoDir.toString() + "/../norm-repo";

        // When
        String resolved = RepoPathResolver.resolve(pathWithDots);

        // Then
        assertFalse(resolved.contains(".."), "Path should be normalized (no '..')");
    }

    @Test
    @DisplayName("Default repo path constant should be set")
    void testDefaultRepoPath_IsNotEmpty() {
        assertNotNull(RepoPathResolver.DEFAULT_REPO_PATH);
        assertFalse(RepoPathResolver.DEFAULT_REPO_PATH.isBlank());
    }
}
