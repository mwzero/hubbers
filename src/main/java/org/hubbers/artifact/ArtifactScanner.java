package org.hubbers.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ArtifactScanner {

    public List<String> listManifestNames(Path root, String folder, String manifestName) {
        Path base = root.resolve(folder);
        if (!Files.exists(base)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(base)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(manifestName)))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan artifacts in " + base, e);
        }
    }
}
