package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Path;

public class LuceneVectorSearchToolDriver implements ToolDriver {
    private static final int DEFAULT_TOP_K = 3;
    private final ObjectMapper mapper;

    public LuceneVectorSearchToolDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "vector.lucene.search";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String indexPath = resolveIndexPath(manifest, input);
        int topK = resolveTopK(manifest, input);
        ArrayNode items = input.path("items").isArray() ? (ArrayNode) input.path("items") : mapper.createArrayNode();

        ArrayNode enriched = mapper.createArrayNode();
        try (FSDirectory directory = FSDirectory.open(LuceneVectorSupport.resolvePath(indexPath));
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                ObjectNode copy = item.isObject() ? ((ObjectNode) item).deepCopy() : mapper.createObjectNode();
                String itemId = LuceneVectorSupport.resolveItemId(item, i);
                float[] vector = LuceneVectorSupport.embed(LuceneVectorSupport.textForEmbedding(item));
                TopDocs topDocs = searcher.search(new KnnFloatVectorQuery("embedding", vector, topK + 1), topK + 1);
                copy.set("retrieved_context", LuceneVectorSupport.toContextArray(mapper, topDocs.scoreDocs, searcher, itemId));
                enriched.add(copy);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lucene search failed", e);
        }

        ObjectNode output = mapper.createObjectNode();
        output.set("items", enriched);
        return output;
    }

    private int resolveTopK(ToolManifest manifest, JsonNode input) {
        JsonNode inputTopK = input.get("top_k");
        if (inputTopK != null && inputTopK.isNumber()) {
            return Math.max(1, inputTopK.asInt());
        }
        Object configured = manifest.getConfig() == null ? null : manifest.getConfig().get("top_k");
        if (configured instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        if (configured != null) {
            try {
                return Math.max(1, Integer.parseInt(configured.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_TOP_K;
    }

    private String resolveIndexPath(ToolManifest manifest, JsonNode input) {
        JsonNode inputPath = input.get("index_path");
        if (inputPath != null && inputPath.isTextual() && !inputPath.asText().isBlank()) {
            return inputPath.asText();
        }
        Object configured = manifest.getConfig() == null ? null : manifest.getConfig().get("index_path");
        if (configured != null && !configured.toString().isBlank()) {
            return configured.toString();
        }
        throw new IllegalArgumentException("Missing index_path");
    }
}
