package org.hubbers.react.tool;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses a {@code tools.md} Markdown table and provides keyword-based search
 * to select a relevant subset of tools for a given user request.
 *
 * <h3>Parsing</h3>
 * Expects rows in the form:
 * <pre>
 * | Description | `&lt;call:tool_name ...&gt;` |
 * </pre>
 *
 * <h3>Scoring</h3>
 * Token overlap between the query and the tool description / name, normalized
 * by the number of query tokens. No external dependencies required.
 *
 * <h3>Pinned tools</h3>
 * Certain tools (e.g. {@code memory_append}) are always included regardless of
 * the query score, because they are infrastructure concerns, not task-specific.
 */
@Slf4j
public class ToolIndex {

    /** Tools that are always injected into the prompt regardless of RAG result. */
    private static final Set<String> PINNED = Set.of("memory_append");

    /** Regex to extract the tool name from a syntax cell like {@code `<call:dir_create ...>}. */
    private static final Pattern SYNTAX_NAME = Pattern.compile("<call:(\\w+)");

    /** Regex to extract the backtick-wrapped syntax cell content. */
    private static final Pattern SYNTAX_CELL = Pattern.compile("`(<call:[^`]+)`");

    private final Map<String, ToolEntry> index = new LinkedHashMap<>();

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Builds a {@link ToolIndex} by parsing the given Markdown table file.
     *
     * @param toolsPath path to {@code tools.md}
     * @throws IOException if the file cannot be read
     */
    public static ToolIndex from(Path toolsPath) throws IOException {
        String content = Files.readString(toolsPath);
        return fromMarkdown(content);
    }

    /**
     * Builds a {@link ToolIndex} from a raw Markdown string (useful for testing).
     */
    public static ToolIndex fromMarkdown(String markdown) {
        ToolIndex idx = new ToolIndex();
        for (String line : markdown.split("\n")) {
            line = line.trim();
            // Skip header, separator, and non-table lines
            if (!line.startsWith("|") || line.startsWith("|---") || line.startsWith("| ---")
                    || line.startsWith("| Desc") || line.startsWith("| **")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length < 3) continue;

            String description = cells[1].trim();
            String syntaxCell  = cells[2].trim();

            // Extract syntax content from backticks
            Matcher syntaxMatcher = SYNTAX_CELL.matcher(syntaxCell);
            if (!syntaxMatcher.find()) continue;
            String syntax = syntaxMatcher.group(1);

            // Extract tool name from syntax
            Matcher nameMatcher = SYNTAX_NAME.matcher(syntax);
            if (!nameMatcher.find()) continue;
            String name = nameMatcher.group(1);

            List<String> tags = tokenize(description + " " + name);
            idx.index.put(name, new ToolEntry(name, description, syntax, tags));
        }
        log.debug("[ToolIndex] Parsed {} tool entries.", idx.index.size());
        return idx;
    }

    // =========================================================================
    // Search
    // =========================================================================

    /**
     * Returns the top-K most relevant {@link ToolEntry} items for the given query,
     * plus all pinned tools.
     *
     * <p>Scoring is based on token overlap: the number of query tokens that also
     * appear in the tool's tags, divided by the total number of query tokens.
     * Score ∈ [0, 1].</p>
     *
     * @param query natural language description of the needed operation
     * @param topK  maximum number of non-pinned tools to return
     * @return ordered list (most relevant first), never {@code null}
     */
    public List<ToolEntry> search(String query, int topK) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            log.debug("[ToolIndex] Empty query — returning all tools.");
            return List.copyOf(index.values());
        }

        Set<String> querySet = Set.copyOf(queryTokens);

        record Scored(ToolEntry entry, double score) {}

        List<Scored> scored = index.values().stream()
                .filter(e -> !PINNED.contains(e.name()))
                .map(e -> {
                    long common = e.tags().stream().filter(querySet::contains).count();
                    double score = (double) common / queryTokens.size();
                    return new Scored(e, score);
                })
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();

        List<ToolEntry> result = new ArrayList<>();

        // Pinned tools first
        PINNED.stream()
              .map(index::get)
              .filter(e -> e != null)
              .forEach(result::add);

        // Top-K scored tools (excluding already-pinned)
        scored.stream()
              .limit(topK)
              .map(Scored::entry)
              .forEach(result::add);

        log.debug("[ToolIndex] Query='{}' → selected {} tools (topK={}, pinned={}).",
                query, result.size(), topK, PINNED.size());
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all entries in the index (full catalog, no scoring).
     */
    public List<ToolEntry> all() {
        return List.copyOf(index.values());
    }

    /** Returns the number of indexed entries. */
    public int size() {
        return index.size();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Splits text into lowercase alphabetic tokens of length ≥ 2, stripping
     * punctuation and stop-words that add no discriminating power.
     */
    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        Set<String> stopWords = Set.of("il", "la", "lo", "le", "i", "gli", "di", "da",
                "in", "con", "su", "per", "tra", "fra", "un", "una", "uno", "the", "a",
                "an", "of", "to", "or", "and", "file", "folder");
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 2)
                .filter(t -> !stopWords.contains(t))
                .distinct()
                .collect(Collectors.toList());
    }
}
