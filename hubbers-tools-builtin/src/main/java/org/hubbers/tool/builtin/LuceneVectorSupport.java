package org.hubbers.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.ScoreDoc;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

final class LuceneVectorSupport {
    static final int VECTOR_DIMENSIONS = 256;
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);

    private LuceneVectorSupport() {
    }

    static Document documentForItem(JsonNode item, int index) {
        String itemId = resolveItemId(item, index);
        String text = textForEmbedding(item);
        float[] vector = embed(text);

        Document doc = new Document();
        doc.add(new StringField("id", itemId, Field.Store.YES));
        doc.add(new KnnFloatVectorField("embedding", vector));
        doc.add(new StoredField("title", textOrEmpty(item.path("title").asText(null))));
        doc.add(new StoredField("summary", textOrEmpty(item.path("summary").asText(null))));
        doc.add(new StoredField("content", textOrEmpty(item.path("content").asText(null))));
        doc.add(new StoredField("source", textOrEmpty(item.path("source").asText(null))));
        doc.add(new StoredField("link", textOrEmpty(item.path("link").asText(null))));
        doc.add(new StoredField("published_at", textOrEmpty(item.path("published_at").asText(null))));
        return doc;
    }

    static ArrayNode toContextArray(ObjectMapper mapper, ScoreDoc[] docs, org.apache.lucene.search.IndexSearcher searcher, String selfId) throws java.io.IOException {
        ArrayNode out = mapper.createArrayNode();
        for (ScoreDoc scoreDoc : docs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String id = doc.get("id");
            if (selfId != null && selfId.equals(id)) {
                continue;
            }
            ObjectNode hit = mapper.createObjectNode();
            hit.put("id", textOrEmpty(id));
            hit.put("score", scoreDoc.score);
            hit.put("title", textOrEmpty(doc.get("title")));
            hit.put("summary", textOrEmpty(doc.get("summary")));
            hit.put("content", textOrEmpty(doc.get("content")));
            hit.put("source", textOrEmpty(doc.get("source")));
            hit.put("link", textOrEmpty(doc.get("link")));
            hit.put("published_at", textOrEmpty(doc.get("published_at")));
            out.add(hit);
        }
        return out;
    }

    static String resolveItemId(JsonNode item, int index) {
        String link = item.path("link").asText(null);
        if (link != null && !link.isBlank()) {
            return link;
        }
        return "item-" + index;
    }

    static String textForEmbedding(JsonNode item) {
        String title = textOrEmpty(item.path("title").asText(null));
        String summary = textOrEmpty(item.path("summary").asText(null));
        String content = textOrEmpty(item.path("content").asText(null));
        return (title + "\n" + summary + "\n" + content).trim();
    }

    static float[] embed(String text) {
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

    static Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(".").resolve(path).normalize();
    }

    static String textOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
