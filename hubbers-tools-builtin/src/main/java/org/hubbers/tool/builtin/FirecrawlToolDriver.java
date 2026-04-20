package org.hubbers.tool.builtin;

import org.hubbers.tool.ToolDriver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.firecrawl.client.FirecrawlClient;
import com.firecrawl.models.CrawlJob;
import com.firecrawl.models.CrawlOptions;
import com.firecrawl.models.Document;
import com.firecrawl.models.MapData;
import com.firecrawl.models.MapOptions;
import com.firecrawl.models.ScrapeOptions;
import com.firecrawl.models.SearchData;
import com.firecrawl.models.SearchOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.config.AppConfig;
import org.hubbers.manifest.tool.ToolManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool driver for Firecrawl web scraping and crawling service.
 * 
 * <p>Supports scrape, crawl, search, and map operations using the Firecrawl API.</p>
 * 
 * @since 0.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class FirecrawlToolDriver implements ToolDriver {
    private final ObjectMapper mapper;
    private final AppConfig appConfig;

    @Override
    public String type() {
        return "firecrawl";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        FirecrawlClient client = createClient(manifest);
        String action = resolveAction(manifest, input);

        return switch (action) {
            case "scrape" -> scrape(client, manifest, input);
            case "crawl" -> crawl(client, manifest, input);
            case "search" -> search(client, input);
            case "map" -> map(client, input);
            default -> throw new IllegalArgumentException("Unsupported firecrawl action: " + action);
        };
    }

    private JsonNode scrape(FirecrawlClient client, ToolManifest manifest, JsonNode input) {
        String url = resolveUrl(manifest, input);
        ScrapeOptions options = ScrapeOptions.builder()
                .formats(resolveFormats(input))
                .onlyMainContent(input.path("only_main_content").asBoolean(true))
                .waitFor(input.path("wait_for").asInt(5000))
                .build();
        Document document = client.scrape(url, options);

        ObjectNode out = mapper.createObjectNode();
        out.put("action", "scrape");
        out.put("url", url);
        out.set("document", mapper.valueToTree(document));
        return out;
    }

    private JsonNode crawl(FirecrawlClient client, ToolManifest manifest, JsonNode input) {
        String url = resolveUrl(manifest, input);
        CrawlOptions options = CrawlOptions.builder()
                .limit(input.path("limit").asInt(10))
                .maxDiscoveryDepth(input.path("max_discovery_depth").asInt(2))
                .scrapeOptions(ScrapeOptions.builder()
                        .formats(resolveFormats(input))
                        .onlyMainContent(input.path("only_main_content").asBoolean(true))
                        .waitFor(input.path("wait_for").asInt(5000))
                        .build())
                .build();
        CrawlJob crawlJob = client.crawl(url, options);

        ObjectNode out = mapper.createObjectNode();
        out.put("action", "crawl");
        out.put("url", url);
        out.set("crawl", mapper.valueToTree(crawlJob));
        return out;
    }

    private JsonNode search(FirecrawlClient client, JsonNode input) {
        String query = text(input, "query");
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Missing input.query for firecrawl search");
        }

        SearchData data = client.search(query, SearchOptions.builder().limit(input.path("limit").asInt(10)).build());

        ObjectNode out = mapper.createObjectNode();
        out.put("action", "search");
        out.put("query", query);
        out.set("results", mapper.valueToTree(data));
        return out;
    }

    private JsonNode map(FirecrawlClient client, JsonNode input) {
        String url = text(input, "url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing input.url for firecrawl map");
        }

        MapOptions.Builder builder = MapOptions.builder()
                .limit(input.path("limit").asInt(100));
        String search = text(input, "search");
        if (search != null && !search.isBlank()) {
            builder.search(search);
        }

        MapData data = client.map(url, builder.build());

        ObjectNode out = mapper.createObjectNode();
        out.put("action", "map");
        out.put("url", url);
        out.set("data", mapper.valueToTree(data));
        return out;
    }

    private FirecrawlClient createClient(ToolManifest manifest) {
        // Priority: 1. manifest config, 2. application.yaml, 3. environment
        String apiKey = config(manifest, "api_key");
        
        if (apiKey == null || apiKey.isBlank()) {
            // Try application.yaml
            if (appConfig != null && appConfig.getTools() != null) {
                apiKey = appConfig.getTools().get("firecrawl", "api_key");
            }
        }
        
        if (apiKey != null && !apiKey.isBlank()) {
            return FirecrawlClient.builder().apiKey(apiKey).build();
        }
        
        // Fallback to environment variable
        return FirecrawlClient.fromEnv();
    }

    private String resolveAction(ToolManifest manifest, JsonNode input) {
        String inputAction = text(input, "action");
        if (inputAction != null && !inputAction.isBlank()) {
            return inputAction.trim().toLowerCase();
        }
        String configAction = config(manifest, "default_action");
        if (configAction != null && !configAction.isBlank()) {
            return configAction.trim().toLowerCase();
        }
        return "scrape";
    }

    private String resolveUrl(ToolManifest manifest, JsonNode input) {
        String inputUrl = text(input, "url");
        if (inputUrl != null && !inputUrl.isBlank()) {
            return inputUrl;
        }
        String configUrl = config(manifest, "base_url");
        if (configUrl != null && !configUrl.isBlank()) {
            return configUrl;
        }
        throw new IllegalArgumentException("Missing url for firecrawl action");
    }

    private List<Object> resolveFormats(JsonNode input) {
        JsonNode formatsNode = input.get("formats");
        if (formatsNode == null || !formatsNode.isArray() || formatsNode.isEmpty()) {
            return List.of((Object) "markdown");
        }

        ArrayNode arrayNode = (ArrayNode) formatsNode;
        List<Object> formats = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            if (node.isTextual()) {
                formats.add(node.asText());
            }
        }

        if (formats.isEmpty()) {
            return List.of((Object) "markdown");
        }
        return formats;
    }

    private String text(JsonNode input, String field) {
        JsonNode node = input.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private String config(ToolManifest manifest, String key) {
        if (manifest.getConfig() == null) {
            return null;
        }
        Object value = manifest.getConfig().get(key);
        return value == null ? null : value.toString();
    }
}
