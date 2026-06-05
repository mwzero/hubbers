package org.hubbers.react.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToolIndex} — parsing and keyword-based search.
 */
class ToolIndexTest {

    /** Minimal tools.md excerpt used by all tests. */
    private static final String SAMPLE_MD = """
            **IMPORTANTE**: ...

            | Descrizione       | Sintassi Esatta (copia letteralmente)                                                           |
            |-------------------|-------------------------------------------------------------------------------------------------|
            | Write a file      | `<call:file_write filename="nome_file" approval="required">contenuto</call>`                    |
            | Read a file       | `<call:file_read filename="nome_file"></call>`                                                  |
            | Delete a file     | `<call:file_delete filename="nome_file" approval="required"></call>`                            |
            | Append to file    | `<call:file_append filename="nome_file" approval="required">testo</call>`                       |
            | Move / Rename     | `<call:file_move src="sorgente" dest="destinazione" approval="required"></call>`                |
            | Copy a file       | `<call:file_copy src="sorgente" dest="destinazione"></call>`                                    |
            | File exists?      | `<call:file_exists filename="nome_file"></call>`                                                |
            | File info         | `<call:file_info filename="nome_file"></call>`                                                  |
            | List files        | `<call:files_list foldername="nome_cartella"></call>`                                           |
            | Folder exists?    | `<call:dir_exists foldername="nome_cartella"></call>`                                           |
            | Create folder     | `<call:dir_create foldername="nome_cartella"></call>`                                           |
            | Delete folder     | `<call:dir_delete foldername="nome_cartella" approval="required"></call>`                       |
            | Search in files   | `<call:files_search foldername="." pattern="testo_da_cercare"></call>`                          |
            | Find files by name| `<call:files_find foldername="cartella" pattern="*.csv"></call>`                                |
            | Files in common   | `<call:files_common foldername="cartella" pattern="*.csv"></call>`                              |
            | Compress          | `<call:file_compress src="sorgente" dest="archivio.zip"></call>`                                |
            | Decompress        | `<call:file_decompress src="archivio.zip" dest="cartella_destinazione"></call>`                 |
            | Append memory     | `<call:memory_append approval="required">testo da aggiungere</call>`                            |
            """;

    // =========================================================================
    // Parsing
    // =========================================================================

    @Test
    void parse_allRows_correctCount() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        // 18 data rows total (all entries in the sample)
        assertEquals(18, idx.size(), "Expected 18 tool entries parsed");
    }

    @Test
    void parse_toolName_extractedCorrectly() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        List<ToolEntry> all = idx.all();
        List<String> names = all.stream().map(ToolEntry::name).toList();
        assertTrue(names.contains("file_write"),   "file_write must be indexed");
        assertTrue(names.contains("dir_create"),   "dir_create must be indexed");
        assertTrue(names.contains("memory_append"), "memory_append must be indexed");
    }

    @Test
    void parse_toolEntry_hasNonEmptyTags() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        for (ToolEntry e : idx.all()) {
            assertFalse(e.tags().isEmpty(),
                    "Tool '" + e.name() + "' should have at least one tag");
        }
    }

    // =========================================================================
    // Search — pinned tool always included
    // =========================================================================

    @Test
    void search_anyQuery_alwaysIncludesMemoryAppend() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        List<ToolEntry> result = idx.search("create folder", 3);
        boolean hasMemory = result.stream().anyMatch(e -> "memory_append".equals(e.name()));
        assertTrue(hasMemory, "memory_append must be pinned and always returned");
    }

    // =========================================================================
    // Search — relevance ranking
    // =========================================================================

    @Test
    void search_createFolder_returnsDir_createFirst() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        // English query as the discovery LLM would produce: "create folder"
        List<ToolEntry> result = idx.search("create folder", 3);

        // memory_append is pinned (first). Among non-pinned, dir_create should rank highest.
        List<String> nonPinned = result.stream()
                .map(ToolEntry::name)
                .filter(n -> !"memory_append".equals(n))
                .toList();
        assertFalse(nonPinned.isEmpty(), "At least one non-pinned tool should be returned");
        assertEquals("dir_create", nonPinned.get(0),
                "dir_create should be ranked #1 for 'create folder'");
    }

    @Test
    void search_compressFile_returnsFile_compress() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        // English query as the discovery LLM would produce: "compress zip archive"
        List<ToolEntry> result = idx.search("compress zip archive", 3);
        List<String> names = result.stream().map(ToolEntry::name).toList();
        assertTrue(names.contains("file_compress"),
                "file_compress must be in result for 'compress zip archive'");
    }

    @Test
    void search_readFile_returnsFile_read() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        List<ToolEntry> result = idx.search("read content", 3);
        List<String> names = result.stream().map(ToolEntry::name).toList();
        assertTrue(names.contains("file_read"),
                "file_read must be in result for 'read content'");
    }

    @Test
    void search_topK_limitsNonPinnedCount() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        int topK = 3;
        List<ToolEntry> result = idx.search("move rename copy", topK);
        long nonPinned = result.stream().filter(e -> !"memory_append".equals(e.name())).count();
        assertEquals(topK, nonPinned,
                "Non-pinned results should be exactly topK=" + topK);
    }

    @Test
    void search_emptyQuery_returnsAllTools() {
        ToolIndex idx = ToolIndex.fromMarkdown(SAMPLE_MD);
        List<ToolEntry> result = idx.search("", 5);
        assertEquals(idx.size(), result.size(),
                "Empty query should return all tools (no filtering)");
    }

    // =========================================================================
    // Tokenizer
    // =========================================================================

    @Test
    void tokenize_removesStopWords() {
        List<String> tokens = ToolIndex.tokenize("crea una cartella di lavoro");
        assertFalse(tokens.contains("una"), "'una' is a stop-word and should be removed");
        assertFalse(tokens.contains("di"),  "'di' is a stop-word and should be removed");
        assertTrue(tokens.contains("crea"));
        assertTrue(tokens.contains("cartella"));
    }

    @Test
    void tokenize_lowercaseAndDistinct() {
        List<String> tokens = ToolIndex.tokenize("File FILE file");
        // 'file' is a stop-word in our set; if not, it must appear exactly once
        long count = tokens.stream().filter("file"::equals).count();
        assertTrue(count <= 1, "No duplicate tokens expected");
    }
}
