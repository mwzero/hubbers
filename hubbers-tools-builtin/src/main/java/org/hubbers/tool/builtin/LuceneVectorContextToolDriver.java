package org.hubbers.tool.builtin;

import org.hubbers.tool.ToolDriver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class LuceneVectorContextToolDriver implements ToolDriver {
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_TOP_K = 3;
    private static final int VECTOR_DIMENSIONS = 256;

    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "vector.lucene.enrich";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String indexPath = asString(manifest, "index_path");
        int topK = resolveTopK(manifest);
        ArrayNode items = input.path("items").isArray() ? (ArrayNode) input.path("items") : mapper.createArrayNode();

        try {
            Path path = resolvePath(indexPath);
            Files.createDirectories(path);

            try (FSDirectory directory = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                for (int i = 0; i < items.size(); i++) {
                    JsonNode item = items.get(i);
                    upsertItem(writer, item, i);
                }
                writer.commit();
            }

            ArrayNode enrichedItems = mapper.createArrayNode();
            try (FSDirectory directory = FSDirectory.open(path);
                 DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                for (int i = 0; i < items.size(); i++) {
                    JsonNode item = items.get(i);
                    ObjectNode copy = item.isObject() ? ((ObjectNode) item).deepCopy() : mapper.createObjectNode();
                    String itemId = resolveItemId(item, i);
                    String text = textForEmbedding(item);
                    float[] vector = embed(text);
                    ArrayNode context = buildContext(searcher, itemId, vector, topK + 1);
                    copy.set("retrieved_context", context);
                    enrichedItems.add(copy);
                }
            }

            ObjectNode output = mapper.createObjectNode();
            output.set("items", enrichedItems);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Lucene vector tool failed", e);
        }
    }

    private void upsertItem(IndexWriter writer, JsonNode item, int index) throws IOException {
        String itemId = resolveItemId(item, index);
        String text = textForEmbedding(item);
        float[] vector = embed(text);

        Document doc = new Document();
        doc.add(new StringField("id", itemId, Field.Store.YES));
        doc.add(new KnnFloatVectorField("embedding", vector));
        doc.add(new StoredField("title", textOrEmpty(item.path("title").asText(null))));
        doc.add(new StoredField("summary", textOrEmpty(item.path("summary").asText(null))));
        doc.add(new StoredField("source", textOrEmpty(item.path("source").asText(null))));
        doc.add(new StoredField("link", textOrEmpty(item.path("link").asText(null))));
        doc.add(new StoredField("published_at", textOrEmpty(item.path("published_at").asText(null))));

        writer.updateDocument(new Term("id", itemId), doc);
    }

    private ArrayNode buildContext(IndexSearcher searcher, String selfId, float[] vector, int k) throws IOException {
        KnnFloatVectorQuery query = new KnnFloatVectorQuery("embedding", vector, k);
        TopDocs topDocs = searcher.search(query, k);

        List<ObjectNode> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String id = doc.get("id");
            if (selfId.equals(id)) {
                continue;
            }
            ObjectNode hit = mapper.createObjectNode();
            hit.put("id", textOrEmpty(id));
            hit.put("score", scoreDoc.score);
            hit.put("title", textOrEmpty(doc.get("title")));
            hit.put("summary", textOrEmpty(doc.get("summary")));
            hit.put("source", textOrEmpty(doc.get("source")));
            hit.put("link", textOrEmpty(doc.get("link")));
            hit.put("published_at", textOrEmpty(doc.get("published_at")));
            hits.add(hit);
        }

        hits.sort(Comparator.comparingDouble(h -> -h.path("score").asDouble()));
        ArrayNode out = mapper.createArrayNode();
        for (ObjectNode hit : hits) {
            out.add(hit);
        }
        return out;
    }

    private String resolveItemId(JsonNode item, int index) {
        String link = item.path("link").asText(null);
        if (link != null && !link.isBlank()) {
            return link;
        }
        return "item-" + index;
    }

    private String textForEmbedding(JsonNode item) {
        String title = textOrEmpty(item.path("title").asText(null));
        String summary = textOrEmpty(item.path("summary").asText(null));
        String content = textOrEmpty(item.path("content").asText(null));
        return (title + "\n" + summary + "\n" + content).trim();
    }

    private float[] embed(String text) {
        float[] vector = new float[VECTOR_DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String[] tokens = TOKEN_SPLITTER.split(text.toLowerCase(Locale.ROOT));
        int count = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int idx = Math.floorMod(token.hashCode(), VECTOR_DIMENSIONS);
            vector[idx] += 1.0f;
            count++;
        }
        if (count == 0) {
            return vector;
        }
        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        if (norm == 0.0f) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
        return vector;
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(".").resolve(path).normalize();
    }

    private int resolveTopK(ToolManifest manifest) {
        Object value = manifest.getConfig() == null ? null : manifest.getConfig().get("top_k");
        if (value instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        if (value != null) {
            try {
                return Math.max(1, Integer.parseInt(value.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_TOP_K;
    }

    private String asString(ToolManifest manifest, String key) {
        Object value = manifest.getConfig() == null ? null : manifest.getConfig().get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return value.toString();
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
