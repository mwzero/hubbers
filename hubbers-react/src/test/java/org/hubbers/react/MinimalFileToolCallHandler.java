package org.hubbers.react;

import org.hubbers.react.execution.RunResult;
import org.hubbers.react.tool.ToolCallHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * File-system tool handler for all built-in file and folder operations.
 */
public class MinimalFileToolCallHandler implements ToolCallHandler {

    private Path memoryFilePath;

    public MinimalFileToolCallHandler(Path memoryFilePath) {
        this.memoryFilePath = memoryFilePath;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public RunResult handle(String artifactName, JsonNode arguments) {
        try {
            return switch (artifactName) {
                case "file_write"      -> handleFileWrite(arguments);
                case "file_read"       -> handleFileRead(arguments);
                case "file_delete"     -> handleFileDelete(arguments);
                case "file_append"     -> handleFileAppend(arguments);
                case "file_move"       -> handleFileMove(arguments);
                case "file_copy"       -> handleFileCopy(arguments);
                case "file_exists"     -> handleFileExists(arguments);
                case "file_info"       -> handleFileInfo(arguments);
                case "files_list"      -> handleFilesList(arguments);
                case "dir_exists"      -> handleDirExists(arguments);
                case "dir_create"      -> handleDirCreate(arguments);
                case "dir_delete"      -> handleDirDelete(arguments);
                case "files_search"    -> handleFilesSearch(arguments);
                case "files_find"      -> handleFilesFind(arguments);
                case "files_common"    -> handleFilesCommon(arguments);
                case "file_compress"   -> handleFileCompress(arguments);
                case "file_decompress" -> handleFileDecompress(arguments);
                case "memory_append"   -> handleMemoryAppend(arguments);
                default -> RunResult.failed("Tool sconosciuto: " + artifactName);
            };
        } catch (Exception e) {
            return RunResult.failed(e.getMessage());
        }
    }

    // =========================================================================
    // File operations
    // =========================================================================

    private RunResult handleFileWrite(JsonNode args) throws IOException {
        Path path = validated(args, "filename");
        Files.writeString(path, args.path("content").asText());
        return ok("File '" + path + "' scritto correttamente.");
    }

    private RunResult handleFileRead(JsonNode args) throws IOException {
        Path path = validated(args, "filename");
        if (!Files.exists(path)) return RunResult.failed("File non trovato: " + path);
        ObjectNode out = MAPPER.createObjectNode();
        out.put("content", Files.readString(path));
        return RunResult.success(out);
    }

    private RunResult handleFileDelete(JsonNode args) throws IOException {
        Path path = validated(args, "filename");
        if (!Files.exists(path)) return RunResult.failed("File non trovato: " + path);
        if (Files.isDirectory(path)) return RunResult.failed("Il path indica una cartella, non un file: " + path);
        Files.delete(path);
        return ok("File '" + path + "' eliminato correttamente.");
    }

    private RunResult handleFileAppend(JsonNode args) throws IOException {
        Path path = validated(args, "filename");
        Files.writeString(path, args.path("content").asText(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return ok("Testo aggiunto al file '" + path + "'.");
    }

    private RunResult handleFileMove(JsonNode args) throws IOException {
        Path src  = validated(args, "src");
        Path dest = validated(args, "dest");
        if (!Files.exists(src)) return RunResult.failed("Sorgente non trovata: " + src);
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        return ok("'" + src + "' spostato in '" + dest + "'.");
    }

    private RunResult handleFileCopy(JsonNode args) throws IOException {
        Path src  = validated(args, "src");
        Path dest = validated(args, "dest");
        if (!Files.exists(src)) return RunResult.failed("Sorgente non trovata: " + src);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        return ok("'" + src + "' copiato in '" + dest + "'.");
    }

    private RunResult handleFileExists(JsonNode args) throws IOException {
        Path path = validated(args, "filename");
        ObjectNode out = MAPPER.createObjectNode();
        out.put("exists", Files.exists(path));
        out.put("filename", path.toString());
        return RunResult.success(out);
    }

    private RunResult handleFileInfo(JsonNode args) throws IOException {
        Path path = validated(args, "filename");
        if (!Files.exists(path)) return RunResult.failed("File non trovato: " + path);
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        ObjectNode out = MAPPER.createObjectNode();
        out.put("filename",      path.toString());
        out.put("size_bytes",    attrs.size());
        out.put("is_directory",  attrs.isDirectory());
        out.put("created",       attrs.creationTime().toString());
        out.put("last_modified", attrs.lastModifiedTime().toString());
        return RunResult.success(out);
    }

    // =========================================================================
    // Directory operations
    // =========================================================================

    private RunResult handleFilesList(JsonNode args) throws IOException {
        Path path = validated(args, "foldername");
        if (!Files.exists(path) || !Files.isDirectory(path))
            return RunResult.failed("Cartella non trovata: " + path);
        ObjectNode out  = MAPPER.createObjectNode();
        var files = MAPPER.createArrayNode();
        try (var stream = Files.list(path)) {
            stream.sorted().forEach(f -> files.add(f.getFileName().toString()));
        }
        out.set("files", files);
        out.put("count", files.size());
        return RunResult.success(out);
    }

    private RunResult handleDirExists(JsonNode args) throws IOException {
        Path path = validated(args, "foldername");
        ObjectNode out = MAPPER.createObjectNode();
        out.put("exists", Files.exists(path) && Files.isDirectory(path));
        out.put("foldername", path.toString());
        return RunResult.success(out);
    }

    private RunResult handleDirCreate(JsonNode args) throws IOException {
        Path path = validated(args, "foldername");
        Files.createDirectories(path);
        return ok("Cartella '" + path + "' creata correttamente.");
    }

    private RunResult handleDirDelete(JsonNode args) throws IOException {
        Path path = validated(args, "foldername");
        if (!Files.exists(path)) return RunResult.failed("Cartella non trovata: " + path);
        if (!Files.isDirectory(path)) return RunResult.failed("Il path non è una cartella: " + path);
        deleteRecursive(path);
        return ok("Cartella '" + path + "' eliminata correttamente.");
    }

    private void deleteRecursive(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); }
                  });
        }
    }

    // =========================================================================
    // Search
    // =========================================================================

    private RunResult handleFilesSearch(JsonNode args) throws IOException {
        Path   folder  = validated(args, "foldername");
        String pattern = args.path("pattern").asText();
        if (!Files.exists(folder) || !Files.isDirectory(folder))
            return RunResult.failed("Cartella non trovata: " + folder);
        ObjectNode out     = MAPPER.createObjectNode();
        var        matches = MAPPER.createArrayNode();
        try (var stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    if (Files.readString(file).contains(pattern)) {
                        matches.add(file.toString());
                    }
                } catch (IOException ignored) {}
            });
        }
        out.set("matches", matches);
        out.put("count", matches.size());
        return RunResult.success(out);
    }

    private RunResult handleFilesFind(JsonNode args) throws IOException {
        String folderRaw = args.path("foldername").asText();
        String pattern   = args.path("pattern").asText();
        Path folder = Paths.get(folderRaw);
        if (!Files.isDirectory(folder))
            return RunResult.failed("Cartella non trovata: " + folder);
        PathMatcher matcher = folder.getFileSystem().getPathMatcher("glob:" + pattern);
        ObjectNode out    = MAPPER.createObjectNode();
        var filesList     = MAPPER.createArrayNode();
        try (var stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> matcher.matches(p.getFileName()))
                  .sorted()
                  .forEach(p -> filesList.add(p.toString()));
        }
        out.set("files", filesList);
        out.put("count", filesList.size());
        return RunResult.success(out);
    }

    /**
     * Returns filenames (basenames only) that appear in ALL immediate subdirectories
     * of the given folder, optionally filtered by a glob pattern.
     * Files present in only some subdirectories are excluded.
     */
    private RunResult handleFilesCommon(JsonNode args) throws IOException {
        String folderRaw = args.path("foldername").asText();
        String pattern   = args.path("pattern").asText("*");
        Path root = Paths.get(folderRaw);
        if (!Files.isDirectory(root))
            return RunResult.failed("Cartella non trovata: " + root);

        PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);

        List<Path> subdirs;
        try (var stream = Files.list(root)) {
            subdirs = stream.filter(Files::isDirectory).sorted().toList();
        }
        if (subdirs.isEmpty())
            return RunResult.failed("Nessuna sottocartella trovata in: " + root);

        // For each subdir collect matching filenames (basename only).
        // Subdirs that contain NO matching files are skipped — they would
        // otherwise make the intersection empty even if all "data" subdirs share
        // the same files (e.g. empty folders created by the user as workspace).
        List<java.util.Set<String>> perDir = new ArrayList<>();
        int skipped = 0;
        for (Path sub : subdirs) {
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            try (var stream = Files.walk(sub)) {
                stream.filter(Files::isRegularFile)
                      .map(Path::getFileName)
                      .filter(matcher::matches)
                      .map(Path::toString)
                      .sorted()
                      .forEach(names::add);
            }
            if (names.isEmpty()) {
                skipped++;
            } else {
                perDir.add(names);
            }
        }

        if (perDir.isEmpty())
            return RunResult.failed("Nessun file corrispondente al pattern '" + pattern + "' trovato nelle sottocartelle di: " + root);

        // Intersection across all non-empty subdirs
        java.util.Set<String> common = new java.util.LinkedHashSet<>(perDir.get(0));
        for (int i = 1; i < perDir.size(); i++) {
            common.retainAll(perDir.get(i));
        }

        ObjectNode out = MAPPER.createObjectNode();
        var arr = MAPPER.createArrayNode();
        common.forEach(arr::add);
        out.set("common_files", arr);
        out.put("count", arr.size());
        out.put("subdirs_checked", perDir.size());
        out.put("subdirs_skipped_empty", skipped);
        return RunResult.success(out);
    }

    // =========================================================================
    // Compress / Decompress
    // =========================================================================

    private RunResult handleFileCompress(JsonNode args) throws IOException {
        String srcRaw = args.path("src").asText();
        Path dest = validated(args, "dest");

        List<Path> filesToCompress = new ArrayList<>();

        // Support recursive glob patterns (e.g. C:\temp\**\*.csv)
        boolean isRecursiveGlob = srcRaw.contains("**");
        if (isRecursiveGlob) {
            int firstDoubleStar = srcRaw.indexOf("**");
            int lastSepBefore   = Math.max(srcRaw.lastIndexOf('/', firstDoubleStar),
                                           srcRaw.lastIndexOf('\\', firstDoubleStar));
            Path baseDir = (lastSepBefore > 0)
                    ? Paths.get(srcRaw.substring(0, lastSepBefore))
                    : Paths.get(".");
            int lastSep   = Math.max(srcRaw.lastIndexOf('/'), srcRaw.lastIndexOf('\\'));
            String fileGlob = (lastSep >= 0) ? srcRaw.substring(lastSep + 1) : srcRaw;
            if (!Files.isDirectory(baseDir))
                return RunResult.failed("Cartella base non trovata: " + baseDir);
            PathMatcher matcher = baseDir.getFileSystem().getPathMatcher("glob:" + fileGlob);
            try (var stream = Files.walk(baseDir)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> matcher.matches(p.getFileName()))
                      .forEach(filesToCompress::add);
            }
            if (filesToCompress.isEmpty())
                return RunResult.failed("Nessun file corrisponde al pattern: " + srcRaw);

        // Support flat glob patterns (e.g. C:\temp\*.txt)
        } else if (srcRaw.contains("*") || srcRaw.contains("?")) {
            boolean hasGlob = true;
            if (hasGlob) {
            // Split manually — never call Paths.get() on a string containing glob chars
            int lastSep = Math.max(srcRaw.lastIndexOf('/'), srcRaw.lastIndexOf('\\'));
            Path parentDir;
            String glob;
            if (lastSep >= 0) {
                parentDir = Paths.get(srcRaw.substring(0, lastSep));
                glob      = srcRaw.substring(lastSep + 1);
            } else {
                parentDir = Paths.get(".");
                glob      = srcRaw;
            }
            if (!Files.isDirectory(parentDir)) {
                return RunResult.failed("Cartella sorgente non trovata: " + parentDir);
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(parentDir, glob)) {
                for (Path p : ds) {
                    if (Files.isRegularFile(p)) filesToCompress.add(p);
                }
            }
            if (filesToCompress.isEmpty()) {
                return RunResult.failed("Nessun file corrisponde al pattern: " + srcRaw);
            }
            } // end hasGlob
        } else {
            Path src = validated(args, "src");
            if (!Files.exists(src)) return RunResult.failed("Sorgente non trovata: " + src);
            if (Files.isDirectory(src)) {
                try (var stream = Files.walk(src)) {
                    stream.filter(Files::isRegularFile).forEach(filesToCompress::add);
                }
            } else {
                filesToCompress.add(src);
            }
        }

        try (OutputStream fos = Files.newOutputStream(dest);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Path file : filesToCompress) {
                zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        return ok("Archivio '" + dest + "' creato con " + filesToCompress.size() + " file.");
    }

    private RunResult handleFileDecompress(JsonNode args) throws IOException {
        Path src  = validated(args, "src");
        Path dest = validated(args, "dest");
        if (!Files.exists(src)) return RunResult.failed("Archivio non trovato: " + src);
        Files.createDirectories(dest);
        try (InputStream fis = Files.newInputStream(src);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = dest.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(dest))
                    throw new IOException("Zip slip detected: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return ok("Archivio '" + src + "' decompresso in '" + dest + "'.");
    }

    // =========================================================================
    // Memory
    // =========================================================================

    private RunResult handleMemoryAppend(JsonNode args) throws IOException {
        Files.writeString(memoryFilePath, "\n- " + args.path("content").asText(),
                StandardOpenOption.APPEND);
        return ok("Nota aggiunta con successo a memory.md.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RunResult ok(String message) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put("message", message);
        return RunResult.success(out);
    }

    /**
     * Validates that the path attribute does not contain directory traversal sequences.
     *
     * @param args     the tool arguments node
     * @param attrName the attribute name holding the path
     * @return the normalized safe path
     * @throws IOException if directory traversal is detected
     */
    private Path validated(JsonNode args, String attrName) throws IOException {
        String raw = args.path(attrName).asText();
        Path normalized = Paths.get(raw).normalize();
        if (normalized.toString().contains("..")) {
            throw new IOException("Directory traversal detected in '" + attrName + "': " + raw);
        }
        return normalized;
    }
}