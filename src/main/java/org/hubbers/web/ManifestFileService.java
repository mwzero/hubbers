package org.hubbers.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class ManifestFileService {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path repoRoot;

    public ManifestFileService(Path repoRoot) {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
    }

    public String readManifest(ManifestType type, String name) {
        Path path = manifestPath(type, name);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read manifest: " + path, e);
        }
    }

    public synchronized void writeManifest(ManifestType type, String name, String yaml) {
        Path path = manifestPath(type, name);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write manifest: " + path, e);
        }
    }

    private Path manifestPath(ManifestType type, String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid manifest name: " + name);
        }
        Path path = repoRoot.resolve(type.folder()).resolve(name).resolve(type.filename()).normalize();
        if (!path.startsWith(repoRoot)) {
            throw new IllegalArgumentException("Invalid manifest path");
        }
        return path;
    }
}
