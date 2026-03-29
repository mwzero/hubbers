package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LuceneVectorUpsertToolDriver implements ToolDriver {
    private final ObjectMapper mapper;

    public LuceneVectorUpsertToolDriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "vector.lucene.upsert";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String indexPath = resolveIndexPath(manifest, input);
        ArrayNode items = input.path("items").isArray() ? (ArrayNode) input.path("items") : mapper.createArrayNode();
        try {
            Path path = LuceneVectorSupport.resolvePath(indexPath);
            Files.createDirectories(path);

            try (FSDirectory directory = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                for (int i = 0; i < items.size(); i++) {
                    JsonNode item = items.get(i);
                    String id = LuceneVectorSupport.resolveItemId(item, i);
                    writer.updateDocument(new Term("id", id), LuceneVectorSupport.documentForItem(item, i));
                }
                writer.commit();
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("index_path", indexPath);
            output.put("upserted", items.size());
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Lucene upsert failed", e);
        }
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
