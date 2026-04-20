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
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.hubbers.manifest.tool.ToolManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RequiredArgsConstructor
public class LuceneKvToolDriver implements ToolDriver {
    private static final String DEFAULT_INDEX_PATH = "./datasets/lucene/kv-store";
    private static final int DEFAULT_LIST_LIMIT = 100;
    
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "lucene.kv";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        String operation = input.path("operation").asText(null);
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Missing required field: operation");
        }

        return switch (operation) {
            case "put" -> executePut(manifest, input);
            case "get" -> executeGet(manifest, input);
            case "delete" -> executeDelete(manifest, input);
            case "batch_put" -> executeBatchPut(manifest, input);
            case "list_keys" -> executeListKeys(manifest, input);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private JsonNode executePut(ToolManifest manifest, JsonNode input) {
        String key = input.path("key").asText(null);
        JsonNode value = input.get("value");
        
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Missing required field: key");
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: value");
        }

        String indexPath = resolveIndexPath(manifest, input);
        
        try {
            Path path = Path.of(indexPath);
            Files.createDirectories(path);
            
            try (FSDirectory directory = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                Document doc = createDocument(key, value);
                writer.updateDocument(new Term("key", key), doc);
                writer.commit();
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("operation", "put");
            output.put("key", key);
            output.set("value", value);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Lucene KV put failed for key: " + key, e);
        }
    }

    private JsonNode executeGet(ToolManifest manifest, JsonNode input) {
        String key = input.path("key").asText(null);
        
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Missing required field: key");
        }

        String indexPath = resolveIndexPath(manifest, input);
        
        try {
            Path path = Path.of(indexPath);
            if (!Files.exists(path)) {
                ObjectNode output = mapper.createObjectNode();
                output.put("success", false);
                output.put("operation", "get");
                output.put("key", key);
                output.putNull("value");
                output.put("error", "Index not found");
                return output;
            }
            
            try (FSDirectory directory = FSDirectory.open(path);
                 DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs results = searcher.search(new TermQuery(new Term("key", key)), 1);
                
                if (results.scoreDocs.length == 0) {
                    ObjectNode output = mapper.createObjectNode();
                    output.put("success", false);
                    output.put("operation", "get");
                    output.put("key", key);
                    output.putNull("value");
                    output.put("error", "Key not found");
                    return output;
                }
                
                Document doc = searcher.doc(results.scoreDocs[0].doc);
                String valueJson = doc.get("value_json");
                JsonNode value = mapper.readTree(valueJson);
                
                ObjectNode output = mapper.createObjectNode();
                output.put("success", true);
                output.put("operation", "get");
                output.put("key", key);
                output.set("value", value);
                return output;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lucene KV get failed for key: " + key, e);
        }
    }

    private JsonNode executeDelete(ToolManifest manifest, JsonNode input) {
        String key = input.path("key").asText(null);
        
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Missing required field: key");
        }

        String indexPath = resolveIndexPath(manifest, input);
        
        try {
            Path path = Path.of(indexPath);
            if (!Files.exists(path)) {
                ObjectNode output = mapper.createObjectNode();
                output.put("success", false);
                output.put("operation", "delete");
                output.put("key", key);
                output.put("error", "Index not found");
                return output;
            }
            
            try (FSDirectory directory = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                writer.deleteDocuments(new Term("key", key));
                writer.commit();
            }

            ObjectNode output = mapper.createObjectNode();
            output.put("success", true);
            output.put("operation", "delete");
            output.put("key", key);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Lucene KV delete failed for key: " + key, e);
        }
    }

    private JsonNode executeBatchPut(ToolManifest manifest, JsonNode input) {
        ArrayNode items = input.path("items").isArray() ? (ArrayNode) input.path("items") : mapper.createArrayNode();
        
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty field: items");
        }

        String indexPath = resolveIndexPath(manifest, input);
        
        try {
            Path path = Path.of(indexPath);
            Files.createDirectories(path);
            
            try (FSDirectory directory = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new KeywordAnalyzer()))) {
                int count = 0;
                for (JsonNode item : items) {
                    String key = item.path("key").asText(null);
                    JsonNode value = item.get("value");
                    
                    if (key != null && !key.isBlank() && value != null) {
                        Document doc = createDocument(key, value);
                        writer.updateDocument(new Term("key", key), doc);
                        count++;
                    }
                }
                writer.commit();

                ObjectNode output = mapper.createObjectNode();
                output.put("success", true);
                output.put("operation", "batch_put");
                output.put("count", count);
                return output;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lucene KV batch_put failed", e);
        }
    }

    private JsonNode executeListKeys(ToolManifest manifest, JsonNode input) {
        int limit = input.path("limit").asInt(DEFAULT_LIST_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_LIST_LIMIT;
        }

        String indexPath = resolveIndexPath(manifest, input);
        
        try {
            Path path = Path.of(indexPath);
            if (!Files.exists(path)) {
                ObjectNode output = mapper.createObjectNode();
                output.put("success", true);
                output.put("operation", "list_keys");
                output.set("keys", mapper.createArrayNode());
                output.put("count", 0);
                return output;
            }
            
            try (FSDirectory directory = FSDirectory.open(path);
                 DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs results = searcher.search(new MatchAllDocsQuery(), limit);
                
                ArrayNode keys = mapper.createArrayNode();
                for (ScoreDoc scoreDoc : results.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String key = doc.get("key");
                    if (key != null) {
                        keys.add(key);
                    }
                }
                
                ObjectNode output = mapper.createObjectNode();
                output.put("success", true);
                output.put("operation", "list_keys");
                output.set("keys", keys);
                output.put("count", keys.size());
                return output;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lucene KV list_keys failed", e);
        }
    }

    private Document createDocument(String key, JsonNode value) throws IOException {
        String valueJson = mapper.writeValueAsString(value);
        long timestamp = System.currentTimeMillis();
        
        Document doc = new Document();
        doc.add(new StringField("key", key, Field.Store.YES));
        doc.add(new StoredField("value_json", valueJson));
        doc.add(new LongPoint("timestamp", timestamp));
        doc.add(new StoredField("timestamp", timestamp));
        return doc;
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
        
        return DEFAULT_INDEX_PATH;
    }
}
