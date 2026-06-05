package org.hubbers.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hubbers.react.execution.ExecutionStatus;
import org.hubbers.react.execution.RunResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MinimalFileToolCallHandler Tests")
class MinimalFileToolCallHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path tempDir;
    private Path memoryFile;
    private MinimalFileToolCallHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        tempDir    = Files.createTempDirectory("hubbers-test");
        memoryFile = tempDir.resolve("memory.md");
        Files.writeString(memoryFile, "");
        handler = new MinimalFileToolCallHandler(memoryFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RunResult handle(String tool, ObjectNode args) {
        return handler.handle(tool, args);
    }

    private ObjectNode args() {
        return MAPPER.createObjectNode();
    }

    private void assertSuccess(RunResult result) {
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus(),
                "Expected SUCCESS but got FAILED: " + result.getError());
    }

    private void assertFailed(RunResult result) {
        assertEquals(ExecutionStatus.FAILED, result.getStatus(),
                "Expected FAILED but got SUCCESS");
    }

    // =========================================================================
    // file_write
    // =========================================================================

    @Test
    @DisplayName("file_write — crea un nuovo file con il contenuto specificato")
    void testFileWrite_WithValidPath_CreatesFile() throws IOException {
        Path target = tempDir.resolve("hello.txt");

        RunResult result = handle("file_write", args()
                .put("filename", target.toString())
                .put("content", "ciao mondo"));

        assertSuccess(result);
        assertTrue(Files.exists(target));
        assertEquals("ciao mondo", Files.readString(target));
    }

    @Test
    @DisplayName("file_write — sovrascrive un file esistente")
    void testFileWrite_WithExistingFile_OverwritesContent() throws IOException {
        Path target = tempDir.resolve("over.txt");
        Files.writeString(target, "vecchio");

        handle("file_write", args().put("filename", target.toString()).put("content", "nuovo"));

        assertEquals("nuovo", Files.readString(target));
    }

    // =========================================================================
    // file_read
    // =========================================================================

    @Test
    @DisplayName("file_read — legge il contenuto di un file esistente")
    void testFileRead_WithExistingFile_ReturnsContent() throws IOException {
        Path target = tempDir.resolve("read.txt");
        Files.writeString(target, "contenuto test");

        RunResult result = handle("file_read", args().put("filename", target.toString()));

        assertSuccess(result);
        assertEquals("contenuto test", result.getOutput().get("content").asText());
    }

    @Test
    @DisplayName("file_read — fallisce se il file non esiste")
    void testFileRead_WithMissingFile_ReturnsFailed() {
        RunResult result = handle("file_read",
                args().put("filename", tempDir.resolve("nonexistent.txt").toString()));

        assertFailed(result);
        assertTrue(result.getError().contains("non trovato"));
    }

    // =========================================================================
    // file_append
    // =========================================================================

    @Test
    @DisplayName("file_append — aggiunge testo in fondo a un file esistente")
    void testFileAppend_WithExistingFile_AppendsContent() throws IOException {
        Path target = tempDir.resolve("append.txt");
        Files.writeString(target, "prima riga\n");

        handle("file_append", args()
                .put("filename", target.toString())
                .put("content", "seconda riga"));

        String content = Files.readString(target);
        assertTrue(content.startsWith("prima riga\n"));
        assertTrue(content.contains("seconda riga"));
    }

    @Test
    @DisplayName("file_append — crea il file se non esiste")
    void testFileAppend_WithMissingFile_CreatesFile() throws IOException {
        Path target = tempDir.resolve("new-append.txt");

        RunResult result = handle("file_append", args()
                .put("filename", target.toString())
                .put("content", "testo iniziale"));

        assertSuccess(result);
        assertTrue(Files.exists(target));
        assertTrue(Files.readString(target).contains("testo iniziale"));
    }

    // =========================================================================
    // file_delete
    // =========================================================================

    @Test
    @DisplayName("file_delete — elimina un file esistente")
    void testFileDelete_WithExistingFile_DeletesFile() throws IOException {
        Path target = tempDir.resolve("del.txt");
        Files.writeString(target, "da eliminare");

        RunResult result = handle("file_delete", args().put("filename", target.toString()));

        assertSuccess(result);
        assertFalse(Files.exists(target));
    }

    @Test
    @DisplayName("file_delete — fallisce se il file non esiste")
    void testFileDelete_WithMissingFile_ReturnsFailed() {
        RunResult result = handle("file_delete",
                args().put("filename", tempDir.resolve("ghost.txt").toString()));

        assertFailed(result);
    }

    @Test
    @DisplayName("file_delete — fallisce se il path è una directory")
    void testFileDelete_WithDirectory_ReturnsFailed() {
        RunResult result = handle("file_delete",
                args().put("filename", tempDir.toString()));

        assertFailed(result);
        assertTrue(result.getError().contains("cartella"));
    }

    // =========================================================================
    // file_move
    // =========================================================================

    @Test
    @DisplayName("file_move — sposta un file nella destinazione")
    void testFileMove_WithExistingFile_MovesFile() throws IOException {
        Path src  = tempDir.resolve("src.txt");
        Path dest = tempDir.resolve("dest.txt");
        Files.writeString(src, "contenuto");

        RunResult result = handle("file_move", args()
                .put("src", src.toString())
                .put("dest", dest.toString()));

        assertSuccess(result);
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("contenuto", Files.readString(dest));
    }

    @Test
    @DisplayName("file_move — fallisce se la sorgente non esiste")
    void testFileMove_WithMissingSrc_ReturnsFailed() {
        RunResult result = handle("file_move", args()
                .put("src", tempDir.resolve("missing.txt").toString())
                .put("dest", tempDir.resolve("dest.txt").toString()));

        assertFailed(result);
    }

    // =========================================================================
    // file_copy
    // =========================================================================

    @Test
    @DisplayName("file_copy — copia un file mantenendo l'originale")
    void testFileCopy_WithExistingFile_CopiesFile() throws IOException {
        Path src  = tempDir.resolve("original.txt");
        Path dest = tempDir.resolve("copy.txt");
        Files.writeString(src, "dati originali");

        RunResult result = handle("file_copy", args()
                .put("src", src.toString())
                .put("dest", dest.toString()));

        assertSuccess(result);
        assertTrue(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("dati originali", Files.readString(dest));
    }

    // =========================================================================
    // file_exists
    // =========================================================================

    @Test
    @DisplayName("file_exists — restituisce true per un file esistente")
    void testFileExists_WithExistingFile_ReturnsTrue() throws IOException {
        Path target = tempDir.resolve("exists.txt");
        Files.writeString(target, "");

        RunResult result = handle("file_exists", args().put("filename", target.toString()));

        assertSuccess(result);
        assertTrue(result.getOutput().get("exists").asBoolean());
    }

    @Test
    @DisplayName("file_exists — restituisce false per un file assente")
    void testFileExists_WithMissingFile_ReturnsFalse() {
        RunResult result = handle("file_exists",
                args().put("filename", tempDir.resolve("nope.txt").toString()));

        assertSuccess(result);
        assertFalse(result.getOutput().get("exists").asBoolean());
    }

    // =========================================================================
    // file_info
    // =========================================================================

    @Test
    @DisplayName("file_info — restituisce metadati corretti per un file esistente")
    void testFileInfo_WithExistingFile_ReturnsMetadata() throws IOException {
        Path target = tempDir.resolve("info.txt");
        Files.writeString(target, "12345");

        RunResult result = handle("file_info", args().put("filename", target.toString()));

        assertSuccess(result);
        JsonNode out = result.getOutput();
        assertFalse(out.get("is_directory").asBoolean());
        assertTrue(out.get("size_bytes").asLong() > 0);
        assertNotNull(out.get("created").asText());
    }

    @Test
    @DisplayName("file_info — fallisce se il file non esiste")
    void testFileInfo_WithMissingFile_ReturnsFailed() {
        RunResult result = handle("file_info",
                args().put("filename", tempDir.resolve("missing.txt").toString()));

        assertFailed(result);
    }

    // =========================================================================
    // files_list
    // =========================================================================

    @Test
    @DisplayName("files_list — elenca i file in una cartella non vuota")
    void testFilesList_WithPopulatedFolder_ReturnsAllFiles() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "");
        Files.writeString(tempDir.resolve("b.txt"), "");
        Files.writeString(tempDir.resolve("c.txt"), "");
        // memory.md is already there from setUp, so count ≥ 4

        RunResult result = handle("files_list", args().put("foldername", tempDir.toString()));

        assertSuccess(result);
        assertTrue(result.getOutput().get("count").asInt() >= 3);
    }

    @Test
    @DisplayName("files_list — fallisce se la cartella non esiste")
    void testFilesList_WithMissingFolder_ReturnsFailed() {
        RunResult result = handle("files_list",
                args().put("foldername", tempDir.resolve("nonexistent").toString()));

        assertFailed(result);
    }

    // =========================================================================
    // dir_exists
    // =========================================================================

    @Test
    @DisplayName("dir_exists — true per una cartella esistente")
    void testDirExists_WithExistingDir_ReturnsTrue() {
        RunResult result = handle("dir_exists", args().put("foldername", tempDir.toString()));

        assertSuccess(result);
        assertTrue(result.getOutput().get("exists").asBoolean());
    }

    @Test
    @DisplayName("dir_exists — false per un path inesistente")
    void testDirExists_WithMissingDir_ReturnsFalse() {
        RunResult result = handle("dir_exists",
                args().put("foldername", tempDir.resolve("ghost").toString()));

        assertSuccess(result);
        assertFalse(result.getOutput().get("exists").asBoolean());
    }

    // =========================================================================
    // dir_create
    // =========================================================================

    @Test
    @DisplayName("dir_create — crea una nuova cartella")
    void testDirCreate_WithNewPath_CreatesDirectory() {
        Path newDir = tempDir.resolve("subdir");

        RunResult result = handle("dir_create", args().put("foldername", newDir.toString()));

        assertSuccess(result);
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    @DisplayName("dir_create — è idempotente su cartella esistente")
    void testDirCreate_WithExistingDir_Succeeds() {
        RunResult result = handle("dir_create", args().put("foldername", tempDir.toString()));

        assertSuccess(result);
    }

    // =========================================================================
    // dir_delete
    // =========================================================================

    @Test
    @DisplayName("dir_delete — elimina ricorsivamente una cartella non vuota")
    void testDirDelete_WithNonEmptyDir_DeletesRecursively() throws IOException {
        Path subDir = tempDir.resolve("toDelete");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file.txt"), "dati");

        RunResult result = handle("dir_delete", args().put("foldername", subDir.toString()));

        assertSuccess(result);
        assertFalse(Files.exists(subDir));
    }

    @Test
    @DisplayName("dir_delete — fallisce se la cartella non esiste")
    void testDirDelete_WithMissingDir_ReturnsFailed() {
        RunResult result = handle("dir_delete",
                args().put("foldername", tempDir.resolve("nothere").toString()));

        assertFailed(result);
    }

    @Test
    @DisplayName("dir_delete — fallisce se il path è un file")
    void testDirDelete_WithFilePath_ReturnsFailed() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "");

        RunResult result = handle("dir_delete", args().put("foldername", file.toString()));

        assertFailed(result);
    }

    // =========================================================================
    // files_search
    // =========================================================================

    @Test
    @DisplayName("files_search — trova file contenenti il pattern specificato")
    void testFilesSearch_WithMatchingFiles_ReturnsMatches() throws IOException {
        Files.writeString(tempDir.resolve("match1.txt"), "il pattern cercato è qui");
        Files.writeString(tempDir.resolve("match2.txt"), "anche qui c'è il pattern cercato");
        Files.writeString(tempDir.resolve("nomatch.txt"), "niente di speciale");

        RunResult result = handle("files_search", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "pattern cercato"));

        assertSuccess(result);
        assertEquals(2, result.getOutput().get("count").asInt());
    }

    @Test
    @DisplayName("files_search — restituisce count 0 se nessun file corrisponde")
    void testFilesSearch_WithNoMatches_ReturnsZero() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "contenuto irrilevante");

        RunResult result = handle("files_search", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "xyzzy_pattern_inesistente"));

        assertSuccess(result);
        assertEquals(0, result.getOutput().get("count").asInt());
    }

    // =========================================================================
    // file_compress
    // =========================================================================

    @Test
    @DisplayName("file_compress — comprime un singolo file in un archivio zip")
    void testFileCompress_WithSingleFile_CreatesZip() throws IOException {
        Path src  = tempDir.resolve("single.txt");
        Path dest = tempDir.resolve("single.zip");
        Files.writeString(src, "contenuto da comprimere");

        RunResult result = handle("file_compress", args()
                .put("src", src.toString())
                .put("dest", dest.toString()));

        assertSuccess(result);
        assertTrue(Files.exists(dest));
        assertTrue(Files.size(dest) > 0);
    }

    @Test
    @DisplayName("file_compress — comprime tutti i file di una cartella")
    void testFileCompress_WithDirectory_CompressesAllFiles() throws IOException {
        Files.writeString(tempDir.resolve("f1.txt"), "uno");
        Files.writeString(tempDir.resolve("f2.txt"), "due");
        Path dest = Files.createTempFile("archive", ".zip");

        RunResult result = handle("file_compress", args()
                .put("src", tempDir.toString())
                .put("dest", dest.toString()));

        assertSuccess(result);
        assertTrue(Files.size(dest) > 0);
        Files.deleteIfExists(dest);
    }

    @Test
    @DisplayName("file_compress — il glob *.txt seleziona solo i file txt")
    void testFileCompress_WithGlobPattern_CompressesOnlyMatchingFiles() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "testo");
        Files.writeString(tempDir.resolve("b.txt"), "testo");
        Files.writeString(tempDir.resolve("c.csv"), "dati");
        Path dest = tempDir.resolve("only-txt.zip");

        RunResult result = handle("file_compress", args()
                .put("src", tempDir + "/*.txt")
                .put("dest", dest.toString()));

        assertSuccess(result);
        // Verify zip contains only .txt entries
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(dest))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                assertTrue(entry.getName().endsWith(".txt"),
                        "Unexpected entry in zip: " + entry.getName());
                count++;
                zis.closeEntry();
            }
        }
        assertEquals(2, count);
    }

    @Test
    @DisplayName("file_compress — glob senza corrispondenze restituisce errore")
    void testFileCompress_WithGlobNoMatches_ReturnsFailed() throws IOException {
        Path dest = tempDir.resolve("empty.zip");

        RunResult result = handle("file_compress", args()
                .put("src", tempDir + "/*.xyz_nonexistent")
                .put("dest", dest.toString()));

        assertFailed(result);
        assertTrue(result.getError().contains("pattern"));
    }

    @Test
    @DisplayName("file_compress — fallisce se la sorgente non esiste")
    void testFileCompress_WithMissingSrc_ReturnsFailed() {
        RunResult result = handle("file_compress", args()
                .put("src", tempDir.resolve("missing.txt").toString())
                .put("dest", tempDir.resolve("out.zip").toString()));

        assertFailed(result);
    }

    @Test
    @DisplayName("file_compress — glob ricorsivo **/*.csv seleziona csv in tutte le sotto-cartelle")
    void testFileCompress_WithRecursiveGlob_CompressesMatchingFilesRecursively() throws IOException {
        Files.writeString(tempDir.resolve("root.csv"), "r");
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("nested.csv"), "n");
        Files.writeString(sub.resolve("skip.txt"), "s");
        Path dest = tempDir.resolve("all-csv.zip");

        RunResult result = handle("file_compress", args()
                .put("src", tempDir + "/**/*.csv")
                .put("dest", dest.toString()));

        assertSuccess(result);
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(dest))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                assertTrue(entry.getName().endsWith(".csv"),
                        "Unexpected entry in zip: " + entry.getName());
                count++;
                zis.closeEntry();
            }
        }
        assertEquals(2, count);
    }

    @Test
    @DisplayName("file_compress — glob ricorsivo senza corrispondenze restituisce errore")
    void testFileCompress_WithRecursiveGlobNoMatches_ReturnsFailed() throws IOException {
        RunResult result = handle("file_compress", args()
                .put("src", tempDir + "/**/*.xyz_nonexistent")
                .put("dest", tempDir.resolve("empty.zip").toString()));

        assertFailed(result);
        assertTrue(result.getError().contains("pattern"));
    }

    // =========================================================================
    // files_find
    // =========================================================================

    @Test
    @DisplayName("files_find — trova file per estensione in modo ricorsivo")
    void testFilesFind_WithMatchingExtension_ReturnsRecursiveResults() throws IOException {
        Files.writeString(tempDir.resolve("a.csv"), "");
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("b.csv"), "");
        Files.writeString(sub.resolve("c.txt"), "");

        RunResult result = handle("files_find", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "*.csv"));

        assertSuccess(result);
        assertEquals(2, result.getOutput().get("count").asInt());
    }

    @Test
    @DisplayName("files_find — restituisce zero se nessun file corrisponde")
    void testFilesFind_WithNoMatches_ReturnsZero() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "");

        RunResult result = handle("files_find", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "*.xyz_nonexistent"));

        assertSuccess(result);
        assertEquals(0, result.getOutput().get("count").asInt());
    }

    @Test
    @DisplayName("files_find — fallisce se la cartella non esiste")
    void testFilesFind_WithMissingFolder_ReturnsFailed() {
        RunResult result = handle("files_find", args()
                .put("foldername", tempDir.resolve("ghost").toString())
                .put("pattern", "*.csv"));

        assertFailed(result);
    }

    // =========================================================================
    // files_common
    // =========================================================================

    @Test
    @DisplayName("files_common — restituisce solo i file presenti in tutte le sottocartelle")
    void testFilesCommon_ReturnsIntersection() throws IOException {
        Path sub1 = Files.createDirectory(tempDir.resolve("sub1"));
        Path sub2 = Files.createDirectory(tempDir.resolve("sub2"));
        Path sub3 = Files.createDirectory(tempDir.resolve("sub3"));

        // common.csv present in all three
        Files.writeString(sub1.resolve("common.csv"), "a");
        Files.writeString(sub2.resolve("common.csv"), "b");
        Files.writeString(sub3.resolve("common.csv"), "c");

        // only1.csv only in sub1 and sub2
        Files.writeString(sub1.resolve("only1.csv"), "x");
        Files.writeString(sub2.resolve("only1.csv"), "y");

        RunResult result = handle("files_common", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "*.csv"));

        assertSuccess(result);
        JsonNode out  = result.getOutput();
        JsonNode arr  = out.get("common_files");
        assertEquals(1, arr.size(), "Only common.csv should be in the intersection");
        assertEquals("common.csv", arr.get(0).asText());
        assertEquals(3, out.get("subdirs_checked").asInt());
        assertEquals(0, out.get("subdirs_skipped_empty").asInt());
    }

    @Test
    @DisplayName("files_common — nessun file in comune restituisce lista vuota")
    void testFilesCommon_NoCommonFiles_ReturnsEmpty() throws IOException {
        Path sub1 = Files.createDirectory(tempDir.resolve("s1"));
        Path sub2 = Files.createDirectory(tempDir.resolve("s2"));

        Files.writeString(sub1.resolve("a.csv"), "1");
        Files.writeString(sub2.resolve("b.csv"), "2");

        RunResult result = handle("files_common", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "*.csv"));

        assertSuccess(result);
        assertEquals(0, result.getOutput().get("common_files").size());
    }

    @Test
    @DisplayName("files_common — sottocartelle vuote vengono ignorate nell'intersezione")
    void testFilesCommon_EmptySubdirsAreSkipped() throws IOException {
        Path sub1  = Files.createDirectory(tempDir.resolve("data1"));
        Path sub2  = Files.createDirectory(tempDir.resolve("data2"));
        Path empty = Files.createDirectory(tempDir.resolve("empty"));

        Files.writeString(sub1.resolve("report.csv"), "r1");
        Files.writeString(sub2.resolve("report.csv"), "r2");
        // empty has no csv files

        RunResult result = handle("files_common", args()
                .put("foldername", tempDir.toString())
                .put("pattern", "*.csv"));

        assertSuccess(result);
        JsonNode out = result.getOutput();
        assertEquals(1, out.get("common_files").size());
        assertEquals("report.csv", out.get("common_files").get(0).asText());
        assertEquals(2, out.get("subdirs_checked").asInt(),  "empty subdir must be excluded from check");
        assertEquals(1, out.get("subdirs_skipped_empty").asInt());
    }

    @Test
    @DisplayName("files_common — cartella inesistente restituisce errore")
    void testFilesCommon_MissingFolder_ReturnsFailed() {
        RunResult result = handle("files_common", args()
                .put("foldername", tempDir.resolve("ghost").toString())
                .put("pattern", "*.csv"));

        assertFailed(result);
    }

    // =========================================================================
    // file_decompress
    // =========================================================================

    @Test
    @DisplayName("file_decompress — estrae correttamente un archivio zip")
    void testFileDecompress_WithValidZip_ExtractsFiles() throws IOException {
        // First create a zip to decompress
        Path src  = tempDir.resolve("tozip.txt");
        Path zip  = tempDir.resolve("archive.zip");
        Path outDir = tempDir.resolve("extracted");
        Files.writeString(src, "estratto");

        handle("file_compress", args()
                .put("src", src.toString())
                .put("dest", zip.toString()));

        RunResult result = handle("file_decompress", args()
                .put("src", zip.toString())
                .put("dest", outDir.toString()));

        assertSuccess(result);
        assertTrue(Files.exists(outDir.resolve("tozip.txt")));
        assertEquals("estratto", Files.readString(outDir.resolve("tozip.txt")));
    }

    @Test
    @DisplayName("file_decompress — fallisce se l'archivio non esiste")
    void testFileDecompress_WithMissingArchive_ReturnsFailed() {
        RunResult result = handle("file_decompress", args()
                .put("src", tempDir.resolve("noarchive.zip").toString())
                .put("dest", tempDir.resolve("out").toString()));

        assertFailed(result);
    }

    // =========================================================================
    // memory_append
    // =========================================================================

    @Test
    @DisplayName("memory_append — aggiunge una riga al file di memoria")
    void testMemoryAppend_WithContent_AppendsToMemoryFile() throws IOException {
        RunResult result = handle("memory_append",
                args().put("content", "nuova nota di test"));

        assertSuccess(result);
        String memory = Files.readString(memoryFile);
        assertTrue(memory.contains("nuova nota di test"));
    }

    // =========================================================================
    // Unknown tool
    // =========================================================================

    @Test
    @DisplayName("handle — tool sconosciuto restituisce errore descrittivo")
    void testHandle_WithUnknownTool_ReturnsFailed() {
        RunResult result = handle("tool_inesistente", args());

        assertFailed(result);
        assertTrue(result.getError().contains("sconosciuto"));
    }

    // =========================================================================
    // Security: directory traversal
    // =========================================================================

    @ParameterizedTest
    @DisplayName("validated — blocca path con sequenze di directory traversal")
    @ValueSource(strings = {"../secret.txt", "subdir/../../etc/passwd"})
    void testValidated_WithTraversalPath_ThrowsException(String maliciousPath) {
        // validated() is called by all handlers; file_read is the simplest entry point
        RunResult result = handle("file_read", args().put("filename", maliciousPath));

        assertFailed(result);
    }
}
